package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.Stake;
import com.nulink.livingratio.repository.StakeRepository;
import com.nulink.livingratio.utils.Web3jUtils;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StakeService {

    private final Web3jUtils web3jUtils;
    private final StakeRepository stakeRepository;

    public StakeService(Web3jUtils web3jUtils, StakeRepository stakeRepository) {
        this.web3jUtils = web3jUtils;
        this.stakeRepository = stakeRepository;
    }

    @Transactional
    public void create(Stake stake){
        stakeRepository.save(stake);
    }

    public List<Stake> findValidStakeByEpoch(String epoch){
        return stakeRepository.findValidStakeByEpoch(web3jUtils.getEpochStartTime(epoch));
    }

    public String totalStakingNode(){
        List<Stake> all = stakeRepository.findAll();
        all = all.stream().filter(stake -> !stake.getAmount().equals("0")).collect(Collectors.toList());
        Set<String> set = all.stream().map(Stake::getUser).collect(Collectors.toSet());
        return String.valueOf(set.size());
    }
}
