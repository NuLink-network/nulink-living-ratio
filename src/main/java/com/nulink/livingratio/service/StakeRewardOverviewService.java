package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.nulink.livingratio.entity.StakeReward;
import com.nulink.livingratio.entity.StakeRewardOverview;
import com.nulink.livingratio.repository.StakeRewardOverviewRepository;
import com.nulink.livingratio.repository.StakeRewardRepository;
import com.nulink.livingratio.utils.RedisService;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@Service
public class StakeRewardOverviewService {

    private final StakeRewardOverviewRepository stakeRewardOverviewRepository;
    private final StakeRewardRepository stakeRewardRepository;
    private final Web3jUtils web3jUtils;

    private final RedisService redisService;

    public StakeRewardOverviewService(StakeRewardOverviewRepository stakeRewardOverviewRepository,
                                      StakeRewardRepository stakeRewardRepository,
                                      Web3jUtils web3jUtils, RedisService redisService) {
        this.stakeRewardOverviewRepository = stakeRewardOverviewRepository;
        this.stakeRewardRepository = stakeRewardRepository;
        this.web3jUtils = web3jUtils;
        this.redisService = redisService;
    }

    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    @Transactional
    public void generateStakeRewardOverview(){
        String previousEpoch = new BigDecimal(web3jUtils.getCurrentEpoch()).subtract(new BigDecimal(1)).toString();
        if (null == stakeRewardOverviewRepository.findByEpoch(previousEpoch)){
            List<StakeReward> previousEpochStakeReward = stakeRewardRepository.findAllByEpochOrderByCreateTime(previousEpoch);
            if (!previousEpochStakeReward.isEmpty()){
                stakeRewardOverviewRepository.save(getStakeRewardOverview(previousEpochStakeReward, previousEpoch));
            }else {
                StakeRewardOverview stakeRewardOverview = new StakeRewardOverview();
                List<StakeRewardOverview> epochBefore = stakeRewardOverviewRepository.findAllByEpochBefore(Integer.parseInt(previousEpoch));
                List<String> reward = epochBefore.stream().map(StakeRewardOverview::getCurrentEpochReward).filter(StringUtils::isNotEmpty).collect(Collectors.toList());
                String sum = sum(reward);
                sum = new BigDecimal(sum).add(new BigDecimal(web3jUtils.getEpochReward(previousEpoch))).toString();
                stakeRewardOverview.setEpoch(previousEpoch);
                stakeRewardOverview.setAccumulatedReward(sum);
                stakeRewardOverview.setCurrentEpochReward(web3jUtils.getEpochReward(previousEpoch));
                stakeRewardOverviewRepository.save(stakeRewardOverview);
            }
        }
    }

    private StakeRewardOverview getStakeRewardOverview(List<StakeReward> stakeRewards, String epoch){
        StakeRewardOverview stakeRewardOverview = new StakeRewardOverview();
        if (!stakeRewards.isEmpty()){
            for (StakeReward stakeReward : stakeRewards) {
                if (StringUtils.isNotEmpty(stakeReward.getLivingRatio())){
                    stakeReward.setValidStakingAmount(new BigDecimal(stakeReward.getStakingAmount()).multiply(new BigDecimal(stakeReward.getLivingRatio())).setScale(0, RoundingMode.HALF_UP).toString());
                }
            }
            stakeRewardOverview.setValidStakingAmount(sum(stakeRewards.stream().map(StakeReward::getValidStakingAmount).filter(StringUtils::isNotEmpty).collect(Collectors.toList())));
            stakeRewardOverview.setTotalStakingAmount(sum(stakeRewards.stream().map(StakeReward::getStakingAmount).filter(StringUtils::isNotEmpty).collect(Collectors.toList())));
            stakeRewardOverview.setTotalStakingNodes(String.valueOf(stakeRewards.size()));
        }
        List<StakeRewardOverview> epochBefore = stakeRewardOverviewRepository.findAllByEpochBefore(Integer.parseInt(epoch));
        List<String> reward = epochBefore.stream().map(StakeRewardOverview::getCurrentEpochReward).filter(StringUtils::isNotEmpty).collect(Collectors.toList());
        String sum = sum(reward);
        sum = new BigDecimal(sum).add(new BigDecimal(web3jUtils.getEpochReward(epoch))).toString();
        stakeRewardOverview.setAccumulatedReward(sum);
        stakeRewardOverview.setCurrentEpochReward(web3jUtils.getEpochReward(epoch));
        stakeRewardOverview.setEpoch(epoch);
        return stakeRewardOverview;
    }

    //@Cacheable(cacheNames = "StakeRewardOverview", key = "#epoch")
    public StakeRewardOverview findEpoch(String epoch){
        String stakeRewardOverviewFindEpoch = "StakeRewardOverview_lastEpoch_" + epoch;
        try {
            Object redisValue = redisService.get(stakeRewardOverviewFindEpoch);
            if (null != redisValue) {
                String v = redisValue.toString();
                return JSONObject.parseObject(v, StakeRewardOverview.class);
            }
        }catch (Exception e){
            log.error("StakeRewardOverview findLastEpoch redis read error：{}", e.getMessage());
        }
        StakeRewardOverview rewardOverview = stakeRewardOverviewRepository.findByEpoch(epoch);
        if (null != rewardOverview){
            try {
                String pvoStr = JSON.toJSONString(rewardOverview, SerializerFeature.WriteNullStringAsEmpty);
                redisService.set(stakeRewardOverviewFindEpoch, pvoStr, 30, TimeUnit.SECONDS);
            }catch (Exception e){
                log.error("StakeRewardOverview findLastEpoch redis write error：{}", e.getMessage());
            }
        }
        return rewardOverview;
    }

    public StakeRewardOverview findCurrentEpoch(){
        String epoch = web3jUtils.getCurrentEpoch();
        String stakeRewardOverviewCurrentEpoch = "StakeRewardOverview_currentEpoch_" + epoch;
        StakeRewardOverview stakeRewardOverview = new StakeRewardOverview();
        try {
            Object redisValue = redisService.get(stakeRewardOverviewCurrentEpoch);
            if (null != redisValue) {
                String v = redisValue.toString();
                stakeRewardOverview = JSONObject.parseObject(v, StakeRewardOverview.class);
                return stakeRewardOverview;
            }
        }catch (Exception e){
            log.error("StakeRewardOverview findCurrentEpoch redis read error：{}", e.getMessage());
        }
        List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpochOrderByCreateTime(epoch);
        if (!stakeRewards.isEmpty()){
            stakeRewardOverview = getStakeRewardOverview(stakeRewards, epoch);
        } else {
            List<StakeRewardOverview> epochBefore = stakeRewardOverviewRepository.findAllByEpochBefore(Integer.parseInt(epoch));
            List<String> reward = epochBefore.stream().map(StakeRewardOverview::getCurrentEpochReward).filter(StringUtils::isNotEmpty).collect(Collectors.toList());
            String sum = sum(reward);
            sum = new BigDecimal(sum).add(new BigDecimal(web3jUtils.getEpochReward(epoch))).toString();
            stakeRewardOverview.setAccumulatedReward(sum);
            stakeRewardOverview.setCurrentEpochReward(web3jUtils.getEpochReward(epoch));
        }
        try {
            String pvoStr = JSON.toJSONString(stakeRewardOverview, SerializerFeature.WriteNullStringAsEmpty);
            redisService.set(stakeRewardOverviewCurrentEpoch, pvoStr, 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("StakeRewardOverview findCurrentEpoch redis write error：{}", e.getMessage());
        }
        return stakeRewardOverview;
    }

    private String sum(List<String> amount){
        BigDecimal sum = BigDecimal.ZERO;
        for (String s : amount) {
            sum = sum.add(new BigDecimal(s));
        }
        return sum.toString();
    }

    public Map<String, String> findTotal(){
        List<StakeRewardOverview> stakeRewardOverviewList = stakeRewardOverviewRepository.findAll();
        StakeRewardOverview currentEpoch = findCurrentEpoch();
        if (currentEpoch != null) {
            stakeRewardOverviewList.add(currentEpoch);
        }
        Map<String, String> result = new HashMap<>();
        if (!stakeRewardOverviewList.isEmpty()){
            List<String> validStakingAmounts = stakeRewardOverviewList.stream().filter(stakeRewardOverview -> StringUtils.isNotEmpty(stakeRewardOverview.getValidStakingAmount())).map(StakeRewardOverview::getValidStakingAmount).collect(Collectors.toList());
            List<String> totalStakingAmounts = stakeRewardOverviewList.stream().filter(stakeRewardOverview -> StringUtils.isNotEmpty(stakeRewardOverview.getTotalStakingAmount())).map(StakeRewardOverview::getTotalStakingAmount).collect(Collectors.toList());
            result.put("validStakingAmount", sum(validStakingAmounts));
            result.put("totalStakingAmount", sum(totalStakingAmounts));
        }
        return result;
    }
}
