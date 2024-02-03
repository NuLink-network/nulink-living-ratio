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
            String currentEpoch = web3jUtils.getCurrentEpoch();
            int i = stakingRewardLeaderboardRepository.countByEpoch(currentEpoch);
            if (i > 0){
                platformTransactionManager.commit(status);
                StakingRewardLeaderboardService.updateLeaderboardTaskFlag = false;
                return;
            }
            List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpoch(currentEpoch);
            if (stakeRewards.isEmpty()){
                platformTransactionManager.commit(status);
                StakingRewardLeaderboardService.updateLeaderboardTaskFlag = false;
                log.info("The update leaderboard task task break, stakeRewards list is empty.");
                return;
            }

            List<StakingRewardLeaderboard> leaderboardList = stakingRewardLeaderboardRepository.findAll();

            Map<String, StakingRewardLeaderboard> leaderboardMap =  new HashMap<>();

            for (StakingRewardLeaderboard stakingRewardLeaderboard : leaderboardList) {
                leaderboardMap.put(stakingRewardLeaderboard.getStakingProvider(), stakingRewardLeaderboard);
            }

            List<StakingRewardLeaderboard> newLeaderboards =  new ArrayList<>();
            for (StakeReward stakeReward : stakeRewards) {
                if (StringUtils.isEmpty(stakeReward.getStakingReward())){
                    stakeReward.setStakingReward("0");
                }
                StakingRewardLeaderboard stakingRewardLeaderboard = leaderboardMap.get(stakeReward.getStakingProvider());
                if (stakingRewardLeaderboard == null){
                    StakingRewardLeaderboard srl = new StakingRewardLeaderboard();
                    srl.setEpoch(currentEpoch);
                    srl.setStakingProvider(stakeReward.getStakingProvider());
                    srl.setAccumulatedStakingReward(stakeReward.getStakingReward());
                    newLeaderboards.add(srl);
                } else {
                    String accumulatedStakingReward =  new BigDecimal(stakingRewardLeaderboard.getAccumulatedStakingReward())
                            .add(new BigDecimal(stakeReward.getStakingReward())).toString();
                    stakingRewardLeaderboard.setAccumulatedStakingReward(accumulatedStakingReward);
                    stakingRewardLeaderboard.setEpoch(currentEpoch);
                    newLeaderboards.add(stakingRewardLeaderboard);
                }
            }

            Comparator<StakingRewardLeaderboard> comparator = Comparator.comparing(srl -> new BigDecimal(srl.getAccumulatedStakingReward()));
            comparator.reversed();
            newLeaderboards.sort(comparator);
            for (int j = 0; j < newLeaderboards.size(); j++) {
                StakingRewardLeaderboard stakingRewardLeaderboard = newLeaderboards.get(j);
                stakingRewardLeaderboard.setRanking(j + 1);
            }
            stakingRewardLeaderboardRepository.saveAll(newLeaderboards);
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
        Sort sort = Sort.by(Sort.Direction.DESC, "ranking");
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);
        return stakingRewardLeaderboardRepository.findAll(pageable);
    }

}
