package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.nulink.livingratio.entity.LeaderboardBlacklist;
import com.nulink.livingratio.entity.StakeReward;
import com.nulink.livingratio.entity.StakeRewardOverview;
import com.nulink.livingratio.entity.StakingRewardLeaderboard;
import com.nulink.livingratio.repository.StakeRewardRepository;
import com.nulink.livingratio.repository.StakingRewardLeaderboardRepository;
import com.nulink.livingratio.utils.RedisService;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@Service
public class StakingRewardLeaderboardService {

    private final StakingRewardLeaderboardRepository stakingRewardLeaderboardRepository;
    private final StakeRewardRepository stakeRewardRepository;

    private final LeaderboardBlacklistService leaderboardBlacklistService;
    private final Web3jUtils web3jUtils;
    private final RedisService redisService;
    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    public StakingRewardLeaderboardService(StakingRewardLeaderboardRepository stakingRewardLeaderboardRepository,
                                           StakeRewardRepository stakeRewardRepository,
                                           LeaderboardBlacklistService leaderboardBlacklistService, Web3jUtils web3jUtils,
                                           RedisService redisService) {
        this.stakingRewardLeaderboardRepository = stakingRewardLeaderboardRepository;
        this.stakeRewardRepository = stakeRewardRepository;
        this.leaderboardBlacklistService = leaderboardBlacklistService;
        this.web3jUtils = web3jUtils;
        this.redisService = redisService;
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

            List<LeaderboardBlacklist> blacklists = leaderboardBlacklistService.findAll(false);
            Set<String> set = blacklists.stream().map(leaderboardBlacklist ->
                leaderboardBlacklist.getStakingProvider().toLowerCase()
            ).collect(Collectors.toSet());

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
                    stakingRewardLeaderboard.setAccumulatedStakingReward(accumulatedStakingReward(stakeReward.getStakingProvider()));
                    leaderboardList.add(stakingRewardLeaderboard);
                }
            }

            Comparator<StakingRewardLeaderboard> comparator = Comparator.comparing(srl -> new BigDecimal(srl.getAccumulatedStakingReward()));
            comparator = comparator.reversed();
            leaderboardList.sort(comparator);
            int ranking = 1;
            for (StakingRewardLeaderboard stakingRewardLeaderboard : leaderboardList) {
                if (set.contains(stakingRewardLeaderboard.getStakingProvider())) {
                    stakingRewardLeaderboard.setRanking(-1);
                } else {
                    stakingRewardLeaderboard.setRanking(ranking);
                    ranking++;
                }

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

    private String accumulatedStakingReward(String stakingProvider){
        List<StakeReward> stakeRewards = stakeRewardRepository.findAllByStakingProvider(stakingProvider);
        BigDecimal accumulatedStakingReward = BigDecimal.ZERO;
        for (StakeReward stakeReward : stakeRewards) {
            String stakingReward = stakeReward.getStakingReward();
            if (StringUtils.isNotBlank(stakingReward)){
                accumulatedStakingReward =  accumulatedStakingReward.add(new BigDecimal(stakingReward));
            }
        }
        return accumulatedStakingReward.toString();
    }

    public StakingRewardLeaderboard findByStakingProvider(String stakingProvider){
        String leaderboardCacheKey = "leaderboardCacheKey_" + stakingProvider;
        try {
            Object value = redisService.get(leaderboardCacheKey);
            if (value != null) {
                String v = value.toString();
                return JSONObject.parseObject(v, StakingRewardLeaderboard.class);
            }
        } catch (Exception e) {
            log.error("StakingRewardLeaderboard findByStakingProvider redis read error: {}", e.getMessage());
        }
        StakingRewardLeaderboard byStakingProviderAndRankingNot = stakingRewardLeaderboardRepository.findByStakingProviderAndRankingNot(stakingProvider, -1);
        if (null != byStakingProviderAndRankingNot){
            try {
                String pvoStr = JSON.toJSONString(byStakingProviderAndRankingNot, SerializerFeature.WriteNullStringAsEmpty);
                redisService.set(leaderboardCacheKey, pvoStr, 1, TimeUnit.HOURS);
            }catch (Exception e){
                log.error("StakingRewardLeaderboard findByStakingProvider redis write error：{}", e.getMessage());
            }
        }
        return byStakingProviderAndRankingNot;
    }

    public Page findByPage(int pageSize, int pageNum){
        String leaderboardPageKey = "leaderboardPage" + pageSize + pageNum;
        String leaderboardPageCountKey = "leaderboardPageCount" + pageSize + pageNum;

        List<StakingRewardLeaderboard> stakingRewardLeaderboards = new ArrayList<>();
        try {
            Object listValue = redisService.get(leaderboardPageKey);
            Object countValue = redisService.get(leaderboardPageCountKey);
            if (listValue != null && countValue != null) {
                String v = listValue.toString();
                stakingRewardLeaderboards = JSONObject.parseArray(v, StakingRewardLeaderboard.class);
                if (!stakingRewardLeaderboards.isEmpty()){
                    return new PageImpl<>(stakingRewardLeaderboards, PageRequest.of(pageNum, pageSize), Long.parseLong(countValue.toString()));
                }
            }
        } catch (Exception e) {
            log.error("stakingRewardLeaderboards find page redis read error: {}", e.getMessage());
        }
        Sort sort = Sort.by(Sort.Direction.ASC, "ranking");
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);
        Specification<StakingRewardLeaderboard> spec = (root, query, cb) -> cb.notEqual(root.get("ranking"), -1);
        Page page = stakingRewardLeaderboardRepository.findAll(spec, pageable);
        List<StakingRewardLeaderboard> content = page.getContent();
        try {
            if (!content.isEmpty()) {
                String pvoStr = JSON.toJSONString(content, SerializerFeature.WriteNullStringAsEmpty);
                redisService.set(leaderboardPageKey, pvoStr, 30, TimeUnit.MINUTES);
                redisService.set(leaderboardPageCountKey, String.valueOf(page.getTotalElements()), 30, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.error("stakingRewardLeaderboards find page redis write error: {}", e.getMessage());
        }
        return page;
    }

}
