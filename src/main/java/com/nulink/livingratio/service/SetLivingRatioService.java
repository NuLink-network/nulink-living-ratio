package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.SetLivingRatio;
import com.nulink.livingratio.entity.StakeReward;
import com.nulink.livingratio.repository.SetLivingRatioRepository;
import com.nulink.livingratio.repository.StakeRewardRepository;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@Service
public class SetLivingRatioService {

    private static final Object setLivingRatioTaskKey = new Object();
    private static boolean lockSetLivingRatioTaskFlag = false;

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

        SetLivingRatio setLivingRatio = setLivingRatioRepository.findFirstBySetLivingRatioOrderByCreateTime(false);
        if (null != setLivingRatio) {
            String epoch = setLivingRatio.getEpoch();
            List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpoch(epoch);

            int batchSize = 500;
            int totalElements = stakeRewards.size();
            int batches = (int) Math.ceil((double) totalElements / batchSize);
            boolean finish = false;
            String txHash = "";
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
                                TimeUnit.MILLISECONDS.sleep(1000);
                            } catch (InterruptedException e) {
                            }
                        } while (j < 20 && (null == txHash || txHash.isEmpty()));
                        TransactionReceipt txReceipt = web3jUtils.waitForTransactionReceipt(txHash);
                        // If status in response equals 1 the transaction was successful. If it is equals 0 the transaction was reverted by EVM.
                        if (Integer.parseInt(txReceipt.getStatus().substring(2), 16) == 0) {
                            log.error("==========>set living ratio failed txHash {} revert reason: {}", txHash, txReceipt.getRevertReason());
                            break;
                        }

                    } catch (IOException | InterruptedException | ExecutionException e) {
                        log.error("==========>set living ratio failed reason: {}", e.getMessage());
                        break;
                    }
                }
            }
            if (finish){
                setLivingRatio.setSetLivingRatio(true);
                setLivingRatio.setTxHash(txHash);
                setLivingRatioRepository.save(setLivingRatio);
            }
        }
        SetLivingRatioService.lockSetLivingRatioTaskFlag = false;
    }
}
