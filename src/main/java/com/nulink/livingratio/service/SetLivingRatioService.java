package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.SetLivingRatio;
import com.nulink.livingratio.entity.StakeReward;
import com.nulink.livingratio.repository.SetLivingRatioRepository;
import com.nulink.livingratio.repository.StakeRewardRepository;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;

import javax.transaction.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@Service
public class SetLivingRatioService {

    private static final Object setLivingRatioTaskKey = new Object();
    private static final Object setUnLivingRatioTaskKey = new Object();
    private static boolean lockSetLivingRatioTaskFlag = false;
    private static boolean lockSetUnLivingRatioTaskFlag = false;

    private final Web3jUtils web3jUtils;
    private final SetLivingRatioRepository setLivingRatioRepository;
    private final StakeRewardRepository stakeRewardRepository;

    public SetLivingRatioService(Web3jUtils web3jUtils,
                                 SetLivingRatioRepository setLivingRatioRepository,
                                 StakeRewardRepository stakeRewardRepository) {
        this.web3jUtils = web3jUtils;
        this.setLivingRatioRepository = setLivingRatioRepository;
        this.stakeRewardRepository = stakeRewardRepository;
    }

    @Transactional
    public void create(SetLivingRatio setLivingRatio){
        setLivingRatioRepository.save(setLivingRatio);
    }

    public List<SetLivingRatio> findUnset(){
        return setLivingRatioRepository.findAllBySetLivingRatioOrderByCreateTimeDesc(false);
    }

    public SetLivingRatio findByEpoch(String epoch){
        return setLivingRatioRepository.findByEpoch(epoch);
    }

    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    @Transactional
    public void setUnLivingRatio(){
        synchronized (setUnLivingRatioTaskKey) {
            if (SetLivingRatioService.lockSetUnLivingRatioTaskFlag) {
                log.warn("The un set living ratio task is already in progress");
                return;
            }
            SetLivingRatioService.lockSetUnLivingRatioTaskFlag = true;
        }
        try{
            String previousEpoch = new BigDecimal(web3jUtils.getCurrentEpoch()).subtract(new BigDecimal(1)).toString();
            SetLivingRatio livingRatio = setLivingRatioRepository.findByEpoch(previousEpoch);
            if (null == livingRatio){
                SetLivingRatio setLivingRatio = new SetLivingRatio();
                setLivingRatio.setEpoch(previousEpoch);
                setLivingRatio.setSetLivingRatio(false);
                setLivingRatioRepository.save(setLivingRatio);
            } else {
                log.warn("The unSet living ratio task has already been executed");
            }
            SetLivingRatioService.lockSetUnLivingRatioTaskFlag = false;
        } catch (Exception e){
            SetLivingRatioService.lockSetUnLivingRatioTaskFlag = false;
        }
    }

    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    @Transactional
    public void setLivingRatio(){
        synchronized (setLivingRatioTaskKey) {
            if (SetLivingRatioService.lockSetLivingRatioTaskFlag) {
                log.warn("The set living ratio task is already in progress");
                return;
            }
            SetLivingRatioService.lockSetLivingRatioTaskFlag = true;
        }

        try {
            log.info("The set living ratio task is starting ...");
            SetLivingRatio setLivingRatio = setLivingRatioRepository.findFirstBySetLivingRatioAndTransactionFailOrderByCreateTime(false, false);
            if (null != setLivingRatio) {
                String epoch = setLivingRatio.getEpoch();
                List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpochAndLivingRatioNot(epoch, "0.0000");
                stakeRewards = stakeRewards.stream().filter(stakeReward -> BigDecimal.ZERO.compareTo(new BigDecimal(stakeReward.getLivingRatio())) != 0).collect(Collectors.toList());
                int batchSize = 100;
                int totalElements = stakeRewards.size();
                int batches = (int) Math.ceil((double) totalElements / batchSize);
                boolean finish = false;
                String txHash = "";
                if (totalElements > 0){
                    for (int i = 0; i < batches; i++) {
                        int fromIndex = i * batchSize;
                        int toIndex = Math.min((i + 1) * batchSize, totalElements);
                        finish = (i + 1) * batchSize >= totalElements;
                        List<StakeReward> batchList = stakeRewards.subList(fromIndex, toIndex);
                        if (!batchList.isEmpty()) {
                            try {
                                List<String> stakingProviders = batchList.stream().map(StakeReward::getStakingProvider).collect(Collectors.toList());
                                List<String> livingRatios = batchList.stream().map(StakeReward::getLivingRatio).collect(Collectors.toList());
                                int j = 0;
                                do {
                                    txHash = web3jUtils.setLiveRatio(epoch, stakingProviders, livingRatios, finish);
                                    j++;
                                    try {
                                        TimeUnit.MILLISECONDS.sleep(10000);
                                    } catch (InterruptedException e) {
                                        SetLivingRatioService.lockSetLivingRatioTaskFlag = false;
                                        return;
                                    }
                                } while (j < 20 && (null == txHash || txHash.isEmpty()));
                                try {
                                    TransactionReceipt txReceipt = web3jUtils.waitForTransactionReceipt(txHash);
                                    // If status in response equals 1 the transaction was successful. If it is equals 0 the transaction was reverted by EVM.
                                    if (Integer.parseInt(txReceipt.getStatus().substring(2), 16) == 0) {
                                        log.error("==========>set living ratio failed txHash {} revert reason: {}", txHash, txReceipt.getRevertReason());
                                        setLivingRatio.setTransactionFail(true);
                                        setLivingRatio.setTxHash(txHash);
                                        setLivingRatio.setReason(txReceipt.getRevertReason());
                                        setLivingRatioRepository.save(setLivingRatio);
                                        SetLivingRatioService.lockSetLivingRatioTaskFlag = false;
                                        return;
                                    }
                                } catch (TransactionException exception){
                                    if(StringUtils.contains(exception.toString(), "Transaction receipt was not generated after")){
                                        setLivingRatio.setTransactionFail(true);
                                        setLivingRatio.setTxHash(txHash);
                                        setLivingRatio.setReason(exception.toString());
                                        setLivingRatioRepository.save(setLivingRatio);
                                        SetLivingRatioService.lockSetLivingRatioTaskFlag = false;
                                        return;
                                    }
                                }
                            } catch (IOException | InterruptedException | ExecutionException e) {
                                log.error("==========>set living ratio failed reason: {}", e.getMessage());
                                SetLivingRatioService.lockSetLivingRatioTaskFlag = false;
                                return;
                            }
                        }
                    }
                } else {
                    finish = true;
                }
                if (finish){
                    setLivingRatio.setSetLivingRatio(true);
                    setLivingRatio.setTxHash(txHash);
                    setLivingRatioRepository.save(setLivingRatio);
                }
            }
        }catch (Exception e){
            log.error("==========>setLivingRatio Task failed reason:", e);
        }finally {
            SetLivingRatioService.lockSetLivingRatioTaskFlag = false;
        }
    }
}
