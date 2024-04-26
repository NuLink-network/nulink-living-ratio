package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.ContractOffset;
import com.nulink.livingratio.entity.SetLivingRatio;
import com.nulink.livingratio.repository.ContractOffsetRepository;
import com.nulink.livingratio.telegram.TelegramBotClient;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class HealthCheckService {

    @Value("${web3j.client-address.official}")
    private String officialRpc;

    private final Web3jUtils web3jUtils;

    private final TelegramBotClient telegramBotClient;

    private final SetLivingRatioService setLivingRatioService;

    private final ContractOffsetRepository contractOffsetRepository;

    public HealthCheckService(Web3jUtils web3jUtils, TelegramBotClient telegramBotClient, SetLivingRatioService setLivingRatioService, ContractOffsetRepository contractOffsetRepository) {
        this.web3jUtils = web3jUtils;
        this.telegramBotClient = telegramBotClient;
        this.setLivingRatioService = setLivingRatioService;
        this.contractOffsetRepository = contractOffsetRepository;
    }

    /**
     * 检测设置在线率任务
     */
    @Async
    @Scheduled(cron = "0 0/5 * * * ? ")
    public void checkStakingReward(){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        String previousEpoch = new BigDecimal(currentEpoch).subtract(new BigDecimal(1)).toString();
        String startTime = web3jUtils.getEpochStartTime(currentEpoch);
        if (System.currentTimeMillis() > (Long.parseLong(startTime) * 1000 + 15 * 60 * 1000)){
            SetLivingRatio livingRatio = setLivingRatioService.findByEpoch(previousEpoch);
            if (!ObjectUtils.isEmpty(livingRatio) && !livingRatio.isSetLivingRatio()){
                LocalDateTime currentTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                log.info("Staking Service Epoch " + previousEpoch + " Set living ratio task is failed, Problem started at " + currentTime.format(formatter));
                telegramBotClient.sendMessage("Project: Staking-Service \n" +
                        "Problem Title: Epoch " + previousEpoch + " Set living ratio task is failed \n" +
                        "Problem started at " + currentTime.format(formatter));
            }
        }
    }

    @Async
    @Scheduled(cron = "0 0/5 * * * ? ")
    public void switchRpcUrl(){
        BigInteger blockNumber = getBlockNumber();
        List<ContractOffset> all = contractOffsetRepository.findAll();
        BigInteger minBlockOffset = all.stream()
                .map(ContractOffset::getBlockOffset)
                .min(BigInteger::compareTo)
                .orElse(BigInteger.ZERO);
        if (blockNumber.subtract(minBlockOffset).compareTo(new BigInteger("100")) > 0){
            web3jUtils.switchRpcUrl();
        }
    }

    @Async
    @Scheduled(cron = "0 0/10 * * * ? ")
    public void checkScannerBlockNumber(){
        BigInteger blockNumber = getBlockNumber();
        List<ContractOffset> all = contractOffsetRepository.findAll();
        BigInteger minBlockOffset = all.stream()
                .map(ContractOffset::getBlockOffset)
                .min(BigInteger::compareTo)
                .orElse(BigInteger.ZERO);
        log.info("blockNumber: " + blockNumber + ", minBlockOffset: " + minBlockOffset);
        if (blockNumber.subtract(minBlockOffset).compareTo(new BigInteger("200")) > 0){
            LocalDateTime currentTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            log.info("The block scanning program has a delay exceeding 200 blocks, Problem started at " + currentTime.format(formatter));
            telegramBotClient.sendMessage("Project: Staking-Service \n" +
                    "Problem Title: Block scanning program has a delay exceeding 200 blocks \n" +
                    "Problem started at " + currentTime.format(formatter));
        }
    }

    /**
     * 检测管理员钱包余额
     */
    @Async
    @Scheduled(cron = "0 0 * * * ?")
    public void checkBalance(){
        BigInteger balance = web3jUtils.getBalance();
        BigDecimal divide = new BigDecimal(balance).divide(new BigDecimal("1000000000000000000"), 4, RoundingMode.HALF_UP);
        if (divide.compareTo(new BigDecimal(5)) < 0){
            LocalDateTime currentTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            System.out.println("The manager's wallet balance is " + divide + "BNB, please recharge. Problem started at " + currentTime.format(formatter));
            telegramBotClient.sendMessage("Project: Staking-Service \n" +
                    "Problem Title: The manager's wallet balance is insufficient \n" +
                    "Wallet balance: " + divide + "BNB \n" +
                    "Problem started at " + currentTime.format(formatter));
        }
    }

    public BigInteger getBlockNumber() {

        Web3j web3j = Web3j.build(new HttpService(officialRpc));

        BigInteger blockNumber = new BigInteger("0");
        try {
            blockNumber = web3j.ethBlockNumber().send().getBlockNumber();
            log.info(" BlockNumber the current block number is {}", blockNumber);
        } catch (IOException e) {
            log.error("get block number failed, IOException: ", e);
        }
        return blockNumber;
    }
}
