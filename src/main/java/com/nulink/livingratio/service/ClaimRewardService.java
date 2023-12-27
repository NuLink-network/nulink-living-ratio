package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.ClaimReward;
import com.nulink.livingratio.repository.ClaimRewardRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
public class ClaimRewardService {

    private final ClaimRewardRepository claimRewardRepository;

    public ClaimRewardService(ClaimRewardRepository claimRewardRepository) {
        this.claimRewardRepository = claimRewardRepository;
    }

    @Transactional
    public void create(ClaimReward claimReward){
        ClaimReward byTxHash = claimRewardRepository.findByTxHash(claimReward.getTxHash());
        if (null == byTxHash){
            claimRewardRepository.save(claimReward);
        }
    }
}
