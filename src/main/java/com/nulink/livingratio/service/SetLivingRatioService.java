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
import java.util.stream.Collectors;

@Log4j2
@Service
public class SetLivingRatioService {

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

    @Async
    @Scheduled(cron = "0 0/2 * * * ? ")
    @Transactional
    public void setLivingRatio(){
        SetLivingRatio setLivingRatio = setLivingRatioRepository.findFirstBySetLivingRatioOrderByCreateTime(false);
        if (setLivingRatio != null) {
            String epoch = setLivingRatio.getEpoch();
            List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpoch(epoch);
            if (!stakeRewards.isEmpty()){
                try {
                    List<String> stakingProviders = stakeRewards.stream().map(StakeReward::getStakingProvider).collect(Collectors.toList());
                    List<String> livingRatios = stakeRewards.stream().map(StakeReward::getLivingRatio).collect(Collectors.toList());
                    String txHash;
                    int i = 0;
                    do {
                        txHash = web3jUtils.setLiveRatio(epoch, stakingProviders, livingRatios);
                        i++;
                    }while (i < 20 && (null == txHash || txHash.isEmpty()));
                    TransactionReceipt txReceipt = web3jUtils.waitForTransactionReceipt(txHash);
                    // If status in response equals 1 the transaction was successful. If it is equals 0 the transaction was reverted by EVM.
                    if (Integer.parseInt(txReceipt.getStatus().substring(2), 16) == 0) {
                        log.error("==========>set living ratio failed txHash {} revert reason: {}", txHash, txReceipt.getRevertReason());
                        throw new RuntimeException(txReceipt.getRevertReason());
                    }
                    setLivingRatio.setSetLivingRatio(true);
                    setLivingRatioRepository.save(setLivingRatio);
                } catch (IOException | InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
