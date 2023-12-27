package com.nulink.livingratio.contract.event.listener.impl;

import com.nulink.livingratio.config.ContractsConfig;
import com.nulink.livingratio.contract.event.listener.consumer.BondEventHandler;
import com.nulink.livingratio.contract.event.listener.consumer.FaucetEventHandler;
import com.nulink.livingratio.contract.event.listener.consumer.StakeEventHandler;
import com.nulink.livingratio.contract.event.listener.filter.events.ContractsEventEnum;
import com.nulink.livingratio.contract.event.listener.filter.events.impl.ContractsEventBuilder;
import com.nulink.livingratio.entity.ContractOffset;
import com.nulink.livingratio.service.ContractOffsetService;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.datatypes.Event;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BlockEventListener {

    public static Logger logger = LoggerFactory.getLogger(BlockEventListener.class);

    private final static BigInteger STEP = new BigInteger("500");

    public static final String BLOCK_CONTRACT_FLAG = "BLOCK_CONTRACT_FLAG";

    @Autowired
    ContractsConfig contractsConfig;

    @Autowired
    private Web3jUtils web3jUtils;

    @Autowired
    private ContractOffsetService contractOffsetService;

    @Value("${contracts.start}")
    public String scannerContractStart;

    private Map<String, Event> topicAndContractAddr2EventMap = new HashMap<>();

    private Map<String, Method> topicAndContractAddr2CallBackMap = new HashMap<>();


    @Value("${contracts.enabled}")
    private boolean enabled;


    public void start(Integer delayBlocks, Set<String> enablesTaskNames, Set<String> disableTaskNames) throws InterruptedException, NoSuchMethodException {
        if (ObjectUtils.isEmpty(delayBlocks)) {
            delayBlocks = 0;
        }

        if (!enabled) {
            log.info("Delay" + delayBlocks + "_" + "BlockEventListener is disabled! ........");
            return;
        }
        logger.info("Delay" + delayBlocks + "_" + "BlockEventListener start");
        initialize(enablesTaskNames, disableTaskNames);
        blocksEventScanner(delayBlocks);
        logger.info("Delay" + delayBlocks + "_" + "BlockEventListener end");
    }

    private boolean isTaskEnable(Set<String> enablesTaskNames, Set<String> disableTaskNames, String curTaskName) {
        curTaskName = curTaskName.toLowerCase();

        boolean disableTaskNamesIsNull = ObjectUtils.isEmpty(disableTaskNames);
        boolean enablesTaskNamesIsNull = ObjectUtils.isEmpty(enablesTaskNames);

        if (disableTaskNamesIsNull && enablesTaskNamesIsNull) {
            return true;
        }
        else if (!disableTaskNamesIsNull && !enablesTaskNamesIsNull) {
            return true;
        } else if (!disableTaskNamesIsNull && !disableTaskNames.contains(curTaskName)) {
            return true;
        } else if (!enablesTaskNamesIsNull && enablesTaskNames.contains(curTaskName)) {
            return true;
        }


        return false;

    }

    public void initialize(Set<String> enablesTaskNames, Set<String> disableTaskNames) throws NoSuchMethodException {
        if (ObjectUtils.isEmpty(enablesTaskNames)) {
            enablesTaskNames = new HashSet<>();
        } else {
            enablesTaskNames = enablesTaskNames.stream().map(String::toLowerCase).collect(Collectors.toSet());
        }

        if (ObjectUtils.isEmpty(disableTaskNames)) {
            disableTaskNames = new HashSet<>();
        } else {
            disableTaskNames = disableTaskNames.stream().map(String::toLowerCase).collect(Collectors.toSet());
        }


        topicAndContractAddr2EventMap.clear();
        topicAndContractAddr2CallBackMap.clear();


        ContractsConfig.ContractInfo stakingManagerCI = contractsConfig.getContractInfo("StakingManager");

        if (isTaskEnable(enablesTaskNames, disableTaskNames, stakingManagerCI.getName()) && stakingManagerCI.getEnabled()) {
            Event operatorBonded = new ContractsEventBuilder().build(ContractsEventEnum.OPERATOR_BONDED);
            String topicEventBuyBlindBox = EventEncoder.encode(operatorBonded).toLowerCase();
            topicAndContractAddr2EventMap.put(topicEventBuyBlindBox + "_" + stakingManagerCI.getAddress(), operatorBonded);
            topicAndContractAddr2CallBackMap.put(topicEventBuyBlindBox + "_" + stakingManagerCI.getAddress(), BondEventHandler.class.getMethod("descOperatorBonded", Log.class /*,secondParameterTypeClass.class*/));
        }

        ContractsConfig.ContractInfo stakingPoolCI = contractsConfig.getContractInfo("NuLinkStakingPool");

        if (isTaskEnable(enablesTaskNames, disableTaskNames, stakingPoolCI.getName()) && stakingPoolCI.getEnabled()) {
            Event stakeEvent = new ContractsEventBuilder().build(ContractsEventEnum.STAKE);
            String topicEventTransfer = EventEncoder.encode(stakeEvent).toLowerCase();
            topicAndContractAddr2EventMap.put(topicEventTransfer + "_" + stakingPoolCI.getAddress(), stakeEvent);
            topicAndContractAddr2CallBackMap.put(topicEventTransfer + "_" + stakingPoolCI.getAddress(), StakeEventHandler.class.getMethod("descStake", Log.class /*,secondParameterTypeClass.class*/));

            Event unStakeAllEvent = new ContractsEventBuilder().build(ContractsEventEnum.UN_STAKE_ALL);
            String topicEventMint = EventEncoder.encode(unStakeAllEvent).toLowerCase();
            topicAndContractAddr2EventMap.put(topicEventMint + "_" + stakingPoolCI.getAddress(), unStakeAllEvent);
            topicAndContractAddr2CallBackMap.put(topicEventMint + "_" + stakingPoolCI.getAddress(), StakeEventHandler.class.getMethod("descUnStakeAll", Log.class/*,secondParameterTypeClass.class*/));

            Event claimEvent = new ContractsEventBuilder().build(ContractsEventEnum.CLAIM);
            String topicEventClaim = EventEncoder.encode(claimEvent).toLowerCase();
            topicAndContractAddr2EventMap.put(topicEventClaim + "_" + stakingPoolCI.getAddress(), claimEvent);
            topicAndContractAddr2CallBackMap.put(topicEventClaim + "_" + stakingPoolCI.getAddress(), StakeEventHandler.class.getMethod("descClaim", Log.class));

            Event claimRewardEvent = new ContractsEventBuilder().build(ContractsEventEnum.CLAIM_REWARD);
            String topicEventClaimReward = EventEncoder.encode(claimRewardEvent).toLowerCase();
            topicAndContractAddr2EventMap.put(topicEventClaimReward + "_" + stakingPoolCI.getAddress(), claimRewardEvent);
            topicAndContractAddr2CallBackMap.put(topicEventClaimReward + "_" + stakingPoolCI.getAddress(), StakeEventHandler.class.getMethod("descClaim", Log.class));
        }

        ContractsConfig.ContractInfo nlkFaucetCI = contractsConfig.getContractInfo("NLKFaucet");

        if (isTaskEnable(enablesTaskNames, disableTaskNames, nlkFaucetCI.getName()) && nlkFaucetCI.getEnabled()) {
            Event faucetNLKEvent = new ContractsEventBuilder().build(ContractsEventEnum.TEST_NLK);
            String topicEventFaucetNLK = EventEncoder.encode(faucetNLKEvent).toLowerCase();
            topicAndContractAddr2EventMap.put(topicEventFaucetNLK + "_" + nlkFaucetCI.getAddress(), faucetNLKEvent);
            topicAndContractAddr2CallBackMap.put(topicEventFaucetNLK + "_" + nlkFaucetCI.getAddress(), FaucetEventHandler.class.getMethod("descFaucetNLKReward", Log.class));
        }

    }

    public void blocksEventScanner(Integer delayBlocks) throws InterruptedException {

        ContractOffset contractOffset = contractOffsetService.findByContractAddress("Delay" + delayBlocks + "_" + BLOCK_CONTRACT_FLAG);
        BigInteger start;
        if (contractOffset == null) {
            start = new BigInteger(scannerContractStart);
        } else {
            start = contractOffset.getBlockOffset();
            if (ObjectUtils.isEmpty(start) || start.compareTo(BigInteger.ZERO) == 0) {
                start = new BigInteger(scannerContractStart);
            }
        }

        logger.info("Delay" + delayBlocks + "_" + "scan all nft albums run() selectMonitorState : " + start);

        BigInteger now = web3jUtils.getBlockNumber(delayBlocks);

        if (start.compareTo(now) >= 0) {
            logger.info("Delay" + delayBlocks + "_" + "scan all nft albums run() return start > now: " + start + " > " + now);
            return;
        }

        while (true) {

            logger.info("Delay" + delayBlocks + "_" + "blocksEventScanner run -------------------");
            if (now.compareTo(BigInteger.ZERO) == 0) {
                logger.info("Delay" + delayBlocks + "_" + "scan all nft albums run() return  now is Zero");
                break;
            }

            BigInteger end = start.add(STEP).compareTo(now) > 0 ? now : start.add(STEP);

            logger.info("Delay" + delayBlocks + "_" + "blocksEventScanner run block [" + start + "," + end + "] ");


            filterEvents(delayBlocks, start, end);

            start = end;

            updateOffset(delayBlocks, end);

            if (end.compareTo(now) >= 0) {
                logger.info("Delay" + delayBlocks + "_" + "scan all nft albums run() return  end > now: " + end + " > " + now);
                break;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
    }

    private void filterEvents(Integer delayBlocks, BigInteger start, BigInteger end) {


        List<Event> events = new ArrayList<>(topicAndContractAddr2EventMap.values());

        try {
            EthLog ethlog = web3jUtils.getEthLogs(start, end, events, contractsConfig.getEnabledContractAddresses()/*can be null */);
            logger.info("Delay" + delayBlocks + "_" + "filterEvents size: " + ethlog.getLogs().size());
            if (!ObjectUtils.isEmpty(ethlog) && ethlog.getLogs().size() > 0) {
                eventDispatcher(delayBlocks, ethlog);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void eventDispatcher(Integer delayBlocks, EthLog logs) {
        for (EthLog.LogResult logResult : logs.getLogs()) {

            Log log = (Log) logResult.get();

            String contractAddress = log.getAddress().toLowerCase(); //合约地址

            String topic = null;
            try {
                topic = log.getTopics().get(0).toLowerCase();
            } catch (Exception e) {
                continue;
            }

            String topicAddress = topic + "_" + contractAddress;
            Method callBackMethod = topicAndContractAddr2CallBackMap.get(topicAddress);
            if (null == callBackMethod) {
                continue;
            }
            try {
                //https://stackoverflow.com/questions/4480334/how-to-call-a-method-stored-in-a-hashmap-java
                // Method format must be: static void functionName(Log, Album)
                logger.info("Delay" + delayBlocks + "_" + "eventDispatcher call function: {} ", callBackMethod.getName());
                callBackMethod.invoke(null, log);

            } catch (Exception e) {
                logger.info("Delay" + delayBlocks + "_" + "scan all nft albums run() function {} exception: {}", callBackMethod.getName(), e.getMessage());
            }

        }

    }

    private void updateOffset(Integer delayBlocks, BigInteger offset) {

        String contractAddress = "Delay" + delayBlocks + "_" + BLOCK_CONTRACT_FLAG;

        ContractOffset contractOffset = contractOffsetService.findByContractAddress(contractAddress);
        if (null == contractOffset) {
            contractOffset = new ContractOffset();
            contractOffset.setContractAddress(contractAddress);
            contractOffset.setContractName("ALL_CONTRACTS");
            contractOffset.setRecordedAt(new Timestamp(new Date().getTime()));
        }
        contractOffset.setBlockOffset(offset);
        contractOffsetService.update(contractOffset);
    }

}
