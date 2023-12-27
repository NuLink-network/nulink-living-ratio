package com.nulink.livingratio.utils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import com.nulink.livingratio.config.ContractsConfig;
import com.nulink.livingratio.config.ProfileConfig;
import com.nulink.livingratio.contract.event.listener.filter.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.admin.Admin;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class Web3jUtils {

    private static String DEV_PROFILE = "dev";
    private static String TEST_PROFILE = "test";
    private static String PROD_PROFILE = "prod";

    public static Logger logger = LoggerFactory.getLogger(Web3jUtils.class);

    /**
     * inject by web3j-spring-boot-starter
     */
    @Resource
    private Web3j web3j;

    /**
     * inject by web3j-spring-boot-starter
     */
    @Resource
    private Admin admin;

    @Value("${NULink.password}")
    private String password;

    @Value("${NULink.chainId}")
    private int chainId;

    private static Credentials credentials;

    @Autowired
    private ProfileConfig profileConfig;

    @Autowired
    ContractsConfig contractsConfig;

    public Web3j getWeb3j() {
        return web3j;
    }

    @PostConstruct
    public void init() throws IOException {
        String keystoreContent = getKeystoreContent();
        credentials = Credentials.create(getPrivateKey(keystoreContent, password));
    }

    private String getKeystoreContent() throws IOException {

        String keystorePath = "";

        String activeProfile = profileConfig.getActiveProfile();

        log.info("The current runtime environment：" + activeProfile);

        if (activeProfile.equals(TEST_PROFILE)){
            keystorePath = "keystore/keystore_test";
        } else if (activeProfile.equals(DEV_PROFILE)) {
            keystorePath = "keystore/keystore_dev";
        } else if(activeProfile.equals(PROD_PROFILE)) {
            keystorePath = "keystore/keystore";
        }

        ClassPathResource classPathResource = new ClassPathResource(keystorePath);
        String data = "";

        byte[] bdata = FileCopyUtils.copyToByteArray(classPathResource.getInputStream());
        data = new String(bdata, StandardCharsets.UTF_8);

        return data;
    }

    public BigInteger getBlockNumber(Integer delayBlocks) {

        BigInteger blockNumber = new BigInteger("0");
        try {
            blockNumber = web3j.ethBlockNumber().send().getBlockNumber();

            if (blockNumber.compareTo(BigInteger.valueOf(delayBlocks)) > 0) {
                blockNumber = blockNumber.subtract(BigInteger.valueOf(delayBlocks));
            }

            logger.info(" getBlockNumber the current block number is {}", blockNumber);

        } catch (IOException e) {
            logger.error("get block number failed, IOException: ", e);
        }
        return blockNumber;
    }

    public EthLog filterEthLog(BigInteger start, BigInteger end, Event event, String contractAddress) throws IOException {
        DefaultBlockParameter startBlock = DefaultBlockParameter.valueOf(start);
        DefaultBlockParameter endBlock = DefaultBlockParameter.valueOf(end);
        org.web3j.protocol.core.methods.request.EthFilter filter = new org.web3j.protocol.core.methods.request.EthFilter(startBlock, endBlock, contractAddress);
        String topic = EventEncoder.encode(event);
        logger.info(" ==========> filterEthLog topic {}", topic);
        filter.addSingleTopic(topic);
        return web3j.ethGetLogs(filter).send();
    }

    /**
     * Retrieve Ethereum event logs
     *
     * @param addresses: can be null
     * @return EthLog
     */
    public EthLog getEthLogs(BigInteger start, BigInteger end, List<Event> events, List<String> addresses/*can be null */) throws IOException {
        org.web3j.protocol.core.methods.request.EthFilter filter = Monitor.getFilter(start, end, events, addresses/* null */);
        EthLog ethlog = web3j.ethGetLogs(filter).send();
        return ethlog;
    }

    /**
     * Get balance
     *
     * @param address Wallet address
     * @return  balance
     */
    private BigInteger getBalance(String address) {
        BigInteger balance = null;
        try {
            EthGetBalance ethGetBalance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
            balance = ethGetBalance.getBalance();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("address " + address + " balance " + balance + "wei");
        return balance;
    }

    /**
     * get Gas Price
     */
    private BigInteger getGasPrice() {
        BigInteger gasPrice = null;
        while (true) {
            try {
                EthGasPrice send = web3j.ethGasPrice().send();
                gasPrice = send.getGasPrice();
                return gasPrice;
            } catch (IOException e) {
                logger.error("can't get gasPrice from private chain exception log: ", e);
            }
        }
    }


    /**
     * Generate a regular transaction object
     *
     * @param fromAddress Sender address
     * @param toAddress Recipient address
     * @param nonce Transaction nonce
     * @param gasPrice Gas price
     * @param gasLimit Gas limit
     * @param value Amount
     * @return Transaction object
     */
    private org.web3j.protocol.core.methods.request.Transaction makeTransaction(String fromAddress, String toAddress, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, BigInteger value) {
        org.web3j.protocol.core.methods.request.Transaction transaction;
        transaction = org.web3j.protocol.core.methods.request.Transaction.createEtherTransaction(fromAddress, nonce, gasPrice, gasLimit, toAddress, value);
        return transaction;
    }

    /**
     * Get gas limit for a regular transaction
     *
     * @param transaction Transaction object
     * @return gas limit
     */
    private BigInteger getTransactionGasLimit(org.web3j.protocol.core.methods.request.Transaction transaction) {
        BigInteger gasLimit = BigInteger.ZERO;
        try {
            EthEstimateGas ethEstimateGas = web3j.ethEstimateGas(transaction).send();
            gasLimit = ethEstimateGas.getAmountUsed();
        } catch (IOException e) {
            // e.printStackTrace();
            logger.error("getTransactionGasLimit IOException log: {}", e);
        }
        return gasLimit;
    }

    /**
     * Get account transaction nonce
     *
     * @param address wallet address
     * @return nonce
     */
    private BigInteger getTransactionNonce(String address) {
        BigInteger nonce = BigInteger.ZERO;
        try {
            EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING).send();
            nonce = ethGetTransactionCount.getTransactionCount();
        } catch (IOException e) {
            logger.error("getTransactionNonce IOException log: {}", e);
        }
        return nonce;
    }

    public String sendTransaction(Function function, String contractAddress) throws IOException, ExecutionException, InterruptedException{

        synchronized (Web3jUtils.class) {
            String encodedFunction = FunctionEncoder.encode(function);

            if (null == credentials) {
                throw new RuntimeException("sendTransaction can't find keystore credentials");
            }

            String fromAddress = credentials.getAddress();
            try {
                EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING).sendAsync().get();
                BigInteger nonce = ethGetTransactionCount.getTransactionCount();

                /*Transaction transaction = Transaction.createFunctionCallTransaction(
                        fromAddress,
                        nonce,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        contractAddress,
                        encodedFunction
                );

                BigInteger transactionGasLimit = getTransactionGasLimit(transaction);*/

                BigInteger ethGasPrice = getGasPrice();

                RawTransaction rawTransaction = RawTransaction.createTransaction( nonce, ethGasPrice, DefaultGasProvider.GAS_LIMIT, contractAddress, encodedFunction);

                byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials);
                String hexValue = Numeric.toHexString(signedMessage);

                EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();
                log.info("sendTransaction txHash: {}", ethSendTransaction.getTransactionHash());
                if (ethSendTransaction.hasError()) {
                    log.error("sendTransaction Error:" + ethSendTransaction.getError().getMessage());
                    throw new EOFException(ethSendTransaction.getError().getMessage());
                }
                return ethSendTransaction.getTransactionHash();

            } catch (Exception e) {
                log.error("sendTransaction Exception:" + e);
                throw e;
            }
        }

    }

    /*
     * Waiting for transaction receipt
     */
    public TransactionReceipt waitForTransactionReceipt(String txHash) {
        // Wait for transaction to be mined
        TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(web3j, TransactionManager.DEFAULT_POLLING_FREQUENCY, TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH);
        TransactionReceipt txReceipt = null;

        int m = 0, retryTimes = 20;
        while (m < retryTimes) {
            try {
                txReceipt = receiptProcessor.waitForTransactionReceipt(txHash);
                break;
            } catch (SocketTimeoutException e) {
                logger.error("waitForTransactionReceipt SocketException:", e);
                try {
                    TimeUnit.MILLISECONDS.sleep(2000);
                } catch (InterruptedException e1) {
                }
                m++;
                if (m < retryTimes) {
                    logger.info("waitForTransactionReceipt SocketException retrying ....");
                }
            } catch (IOException e) {
                // throw new RuntimeException(e);
                logger.error("waitForTransactionReceipt IOException:" + e);
                break;
            } catch (TransactionException e) {
                // throw new RuntimeException(e);
                logger.error("waitForTransactionReceipt TransactionException:" + e);
                break;
            }
        }
        return txReceipt;
    }

    public List<Type> callContractFunction(Function function, String contractAddress) throws ExecutionException, InterruptedException {

        if (null == credentials) {
            throw new RuntimeException("sendTransaction can't find keystore credentials");
        }

        String fromAddress = credentials.getAddress();

        String encodedFunction = FunctionEncoder.encode(function);
        /*org.web3j.protocol.core.methods.response.EthCall*/
        EthCall response = web3j.ethCall(Transaction.createEthCallTransaction(fromAddress, contractAddress, encodedFunction), DefaultBlockParameterName.LATEST).sendAsync().get();

        return FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    }


    /**
     * get PrivateKey
     *
     * @param keystoreContent account keystore
     * @param password
     * @return privateKey
     */
    private String getPrivateKey(String keystoreContent, String password) {
        try {
            Credentials credentials = WalletUtils.loadJsonCredentials(password, keystoreContent);
            BigInteger privateKey = credentials.getEcKeyPair().getPrivateKey();
            return privateKey.toString(16);
        } catch (IOException | CipherException e) {
            logger.error("getPrivateKey Exception:" + e);
        }
        return null;
    }

    /**
     * generate Keystore
     *
     * @param privateKey privateKey
     * @param password   password
     * @param directory  directory
     */
    private static void generateKeystore(byte[] privateKey, String password, String directory) {
        ECKeyPair ecKeyPair = ECKeyPair.create(privateKey);
        try {
            String keystoreName = WalletUtils.generateWalletFile(password, ecKeyPair, new File(directory), true);
            System.out.println("keystore name " + keystoreName);
        } catch (CipherException | IOException e) {
            logger.error("generateKeystore Exception:" + e);
        }
    }

    public static boolean isSignatureValid(final String address, final String signature, final String message) {

        final String personalMessagePrefix = "\u0019Ethereum Signed Message:\n";
        boolean match = false;

        final String prefix = personalMessagePrefix + message.length();
        final byte[] msgHash = Hash.sha3((prefix + message).getBytes());
        final byte[] signatureBytes = Numeric.hexStringToByteArray(signature);
        byte v = signatureBytes[64];
        if (v < 27) {
            v += 27;
        }

        final Sign.SignatureData sd = new Sign.SignatureData(v,
                Arrays.copyOfRange(signatureBytes, 0, 32),
                Arrays.copyOfRange(signatureBytes, 32, 64));

        String addressRecovered = null;

        // Iterate for each possible key to recover
        for (int i = 0; i < 4; i++) {
            final BigInteger publicKey = Sign.recoverFromSignature((byte) i, new ECDSASignature(
                    new BigInteger(1, sd.getR()),
                    new BigInteger(1, sd.getS())), msgHash);

            if (publicKey != null) {
                addressRecovered = "0x" + Keys.getAddress(publicKey);
                logger.info("recovery public address {} {} ", i + 1, addressRecovered);
                if (addressRecovered.equalsIgnoreCase(address)) {
                    match = true;
                    break;
                }
            }
        }

        return match;
    }

    public Timestamp getEventHappenedTimeStamp(String transactionHash) {

        while (true) {
            try {
                return new Timestamp(web3j.ethGetBlockByHash(web3j.ethGetTransactionReceipt(transactionHash).send().getResult().getBlockHash(), true).send().getResult().getTimestamp().longValueExact() * 1000);
            } catch (IOException e) {
                logger.error("getEventHappenedTimeStamp IOException: {} retrying ...", e, e);
            }
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public String getCurrentEpoch() {
        List<Type> inputParameters = new ArrayList<>();
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        outputParameters.add(new TypeReference<Uint16>() {});
        Function function = new Function("getCurrentEpoch", inputParameters, outputParameters);

        ContractsConfig.ContractInfo eventReferralRewardCI = contractsConfig.getContractInfo("NuLinkStakingSetting");

        try {
            List<Type> returnList = callContractFunction(function, eventReferralRewardCI.getAddress());
            return returnList.get(0).getValue().toString();
        } catch (ExecutionException e) {
            // throw new RuntimeException(e);
            log.error("call getCurrentEpoch ExecutionException: {}", e);
        } catch (InterruptedException e) {
            //throw new RuntimeException(e);
            log.error("call getCurrentEpoch InterruptedException: {}", e);
        }
        return null;
    }

    public String getEpochReward(String epoch) {
        List<Type> inputParameters = new ArrayList<>();
        inputParameters.add(new Uint16(Long.valueOf(epoch)));
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        outputParameters.add(new TypeReference<Uint256>() {});
        Function function = new Function("getEpochStakingReward", inputParameters, outputParameters);

        ContractsConfig.ContractInfo eventReferralRewardCI = contractsConfig.getContractInfo("NuLinkStakingSetting");

        try {
            List<Type> returnList = callContractFunction(function, eventReferralRewardCI.getAddress());
            return returnList.get(0).getValue().toString();
        } catch (ExecutionException e) {
            // throw new RuntimeException(e);
            log.error("call getEpochReward ExecutionException: {}", e);
        } catch (InterruptedException e) {
            //throw new RuntimeException(e);
            log.error("call getEpochReward InterruptedException: {}", e);
        }
        return null;
    }

    public String getStartTime() {
        List<Type> inputParameters = new ArrayList<>();
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        outputParameters.add(new TypeReference<Uint256>() {});
        Function function = new Function("startTime", inputParameters, outputParameters);

        ContractsConfig.ContractInfo eventReferralRewardCI = contractsConfig.getContractInfo("NuLinkStakingSetting");

        try {
            List<Type> returnList = callContractFunction(function, eventReferralRewardCI.getAddress());
            return returnList.get(0).getValue().toString();
        } catch (ExecutionException e) {
            // throw new RuntimeException(e);
            log.error("call getStartTime ExecutionException: {}", e);
        } catch (InterruptedException e) {
            //throw new RuntimeException(e);
            log.error("call getStartTime InterruptedException: {}", e);
        }
        return null;
    }

    public String getEpochTimeLen() {
        List<Type> inputParameters = new ArrayList<>();
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        outputParameters.add(new TypeReference<Uint256>() {});
        Function function = new Function("epochTimeLen", inputParameters, outputParameters);

        ContractsConfig.ContractInfo eventReferralRewardCI = contractsConfig.getContractInfo("NuLinkStakingSetting");

        try {
            List<Type> returnList = callContractFunction(function, eventReferralRewardCI.getAddress());
            return returnList.get(0).getValue().toString();
        } catch (ExecutionException e) {
            // throw new RuntimeException(e);
            log.error("call getEpochTimeLen ExecutionException: {}", e);
        } catch (InterruptedException e) {
            //throw new RuntimeException(e);
            log.error("call getEpochTimeLen InterruptedException: {}", e);
        }
        return null;
    }

    public String getEpochStartTime(String epoch){
        List<Type> inputParameters = new ArrayList<>();
        inputParameters.add(new Uint16(Long.parseLong(epoch)));
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        outputParameters.add(new TypeReference<Uint256>() {});
        outputParameters.add(new TypeReference<Uint256>() {});
        Function function = new Function("getEpochTime", inputParameters, outputParameters);

        ContractsConfig.ContractInfo eventReferralRewardCI = contractsConfig.getContractInfo("NuLinkStakingSetting");

        try {
            List<Type> returnList = callContractFunction(function, eventReferralRewardCI.getAddress());
            return returnList.get(0).getValue().toString();
        } catch (ExecutionException e) {
            // throw new RuntimeException(e);
            log.error("call getEpochStartTime ExecutionException: {}", e);
        } catch (InterruptedException e) {
            //throw new RuntimeException(e);
            log.error("call getEpochStartTime InterruptedException: {}", e);
        }
        return null;
    }

    public String getEpochEndTime(String epoch){
        List<Type> inputParameters = new ArrayList<>();
        inputParameters.add(new Uint16(Long.parseLong(epoch)));
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        outputParameters.add(new TypeReference<Uint256>() {});
        outputParameters.add(new TypeReference<Uint256>() {});
        Function function = new Function("getEpochTime", inputParameters, outputParameters);

        ContractsConfig.ContractInfo eventReferralRewardCI = contractsConfig.getContractInfo("NuLinkStakingSetting");

        try {
            List<Type> returnList = callContractFunction(function, eventReferralRewardCI.getAddress());
            return returnList.get(1).getValue().toString();
        } catch (ExecutionException e) {
            // throw new RuntimeException(e);
            log.error("call getEpochStartTime ExecutionException: {}", e);
        } catch (InterruptedException e) {
            //throw new RuntimeException(e);
            log.error("call getEpochStartTime InterruptedException: {}", e);
        }
        return null;
    }

    public String setLiveRatio(String epoch, List<String> stakingProviders, List<String> liveRatios) throws IOException, ExecutionException, InterruptedException {

        List<Address> addresses = stakingProviders.stream().map(Address::new).collect(Collectors.toList());

        List<Uint16> list = liveRatios.stream().map(liveRatio -> new Uint16(new BigDecimal(liveRatio).multiply(new BigDecimal(10000)).longValue())).collect(Collectors.toList());

        List<Type> inputParameters = new ArrayList<>();
        inputParameters.add(new Uint16(Long.parseLong(epoch)));
        inputParameters.add(new Bool(true));
        inputParameters.add(new DynamicArray(Address.class, addresses));
        inputParameters.add(new DynamicArray(Uint16.class, list));
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        Function function = new Function("setLiveRatio", inputParameters, outputParameters);
        ContractsConfig.ContractInfo eventReferralRewardCI = contractsConfig.getContractInfo("NuLinkStakingPool");
        return sendTransaction(function, eventReferralRewardCI.getAddress());
    }
}
