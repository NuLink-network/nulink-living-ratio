package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.StakeReward;
import com.nulink.livingratio.entity.StakingRewardLeaderboard;
import com.nulink.livingratio.repository.StakeRewardRepository;
import com.nulink.livingratio.repository.StakingRewardLeaderboardRepository;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

@Log4j2
@Service
public class StakingRewardLeaderboardService {

    private final StakingRewardLeaderboardRepository stakingRewardLeaderboardRepository;
    private final StakeRewardRepository stakeRewardRepository;
    private final Web3jUtils web3jUtils;
    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    public StakingRewardLeaderboardService(StakingRewardLeaderboardRepository stakingRewardLeaderboardRepository,
                                           StakeRewardRepository stakeRewardRepository,
                                           Web3jUtils web3jUtils) {
        this.stakingRewardLeaderboardRepository = stakingRewardLeaderboardRepository;
        this.stakeRewardRepository = stakeRewardRepository;
        this.web3jUtils = web3jUtils;
    }

    private static final Object updateLeaderboardTaskKey = new Object();
    private static boolean updateLeaderboardTaskFlag = false;

    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    public void updateLeaderboard(){
        synchronized (updateLeaderboardTaskKey) {
            if (StakingRewardLeaderboardService.updateLeaderboardTaskFlag) {
                log.warn("The update leaderboard task is already in progress");
                return;
            }
            StakingRewardLeaderboardService.updateLeaderboardTaskFlag = true;
        }

        log.info("The update leaderboard task task is beginning");
        DefaultTransactionDefinition transactionDefinition= new DefaultTransactionDefinition();
        transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = platformTransactionManager.getTransaction(transactionDefinition);
        try{
            String previousEpoch = new BigDecimal(web3jUtils.getCurrentEpoch()).subtract(new BigDecimal(1)).toString();
            int i = stakingRewardLeaderboardRepository.countByEpoch(previousEpoch);
            if (i > 0){
                platformTransactionManager.commit(status);
                StakingRewardLeaderboardService.updateLeaderboardTaskFlag = false;
                return;
            }
            List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpoch(previousEpoch);
            if (stakeRewards.isEmpty()){
                platformTransactionManager.commit(status);
                StakingRewardLeaderboardService.updateLeaderboardTaskFlag = false;
                log.info("The update leaderboard task task break, stakeRewards list is empty.");
                return;
            }
            if (null == stakeRewards.get(0).getStakingReward()){
                platformTransactionManager.commit(status);
                StakingRewardLeaderboardService.updateLeaderboardTaskFlag = false;
                log.info("Waiting for The count Previous Epoch Stake Reward task finish");
                return;
            }

            Map<String, StakeReward> stakingRewardMap = new HashMap<>();

            for (StakeReward stakeReward : stakeRewards) {
                stakingRewardMap.put(stakeReward.getStakingProvider(), stakeReward);
            }

            List<StakingRewardLeaderboard> leaderboardList = stakingRewardLeaderboardRepository.findAll();

            Map<String, String> leaderboardMap =  new HashMap<>();

            for (StakingRewardLeaderboard stakingRewardLeaderboard : leaderboardList) {
                String stakingProvider = stakingRewardLeaderboard.getStakingProvider();
                String accumulatedStakingReward = stakingRewardLeaderboard.getAccumulatedStakingReward();
                if (accumulatedStakingReward == null){
                    accumulatedStakingReward = "0";
                }
                leaderboardMap.put(stakingProvider, accumulatedStakingReward);
                stakingRewardLeaderboard.setEpoch(previousEpoch);
                StakeReward stakeReward = stakingRewardMap.get(stakingProvider);
                if (null != stakeReward){
                    stakingRewardLeaderboard.setAccumulatedStakingReward(
                            new BigDecimal(accumulatedStakingReward).add(new BigDecimal(stakeReward.getStakingReward() == null? "0" : stakeReward.getStakingReward())).toString());
                }
            }

            for (StakeReward stakeReward : stakeRewards) {
                if (!leaderboardMap.containsKey(stakeReward.getStakingProvider())){
                    StakingRewardLeaderboard stakingRewardLeaderboard = new StakingRewardLeaderboard();
                    stakingRewardLeaderboard.setStakingProvider(stakeReward.getStakingProvider());
                    stakingRewardLeaderboard.setEpoch(previousEpoch);
                    stakingRewardLeaderboard.setAccumulatedStakingReward(stakeReward.getStakingReward());
                    leaderboardList.add(stakingRewardLeaderboard);
                }
            }

            Comparator<StakingRewardLeaderboard> comparator = Comparator.comparing(srl -> new BigDecimal(srl.getAccumulatedStakingReward()));
            comparator = comparator.reversed();
            leaderboardList.sort(comparator);
            for (int j = 0; j < leaderboardList.size(); j++) {
                StakingRewardLeaderboard stakingRewardLeaderboard = leaderboardList.get(j);
                stakingRewardLeaderboard.setRanking(j + 1);
            }
            stakingRewardLeaderboardRepository.saveAll(leaderboardList);
            platformTransactionManager.commit(status);
            StakingRewardLeaderboardService.updateLeaderboardTaskFlag = false;
            log.info("The update leaderboard task task is finish");
        } catch (Exception e){
            log.error("The update leaderboard task task is fail", e);
            platformTransactionManager.rollback(status);
            StakingRewardLeaderboardService.updateLeaderboardTaskFlag = false;
        }
    }

    public StakingRewardLeaderboard findByStakingProvider(String stakingProvider){
        return stakingRewardLeaderboardRepository.findByStakingProvider(stakingProvider);
    }

    public Page<StakingRewardLeaderboard> findByPage(int pageSize, int pageNum){
        Sort sort = Sort.by(Sort.Direction.ASC, "ranking");
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);
        return stakingRewardLeaderboardRepository.findAll(pageable);
    }

}
