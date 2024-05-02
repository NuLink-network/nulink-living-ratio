package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.nulink.livingratio.entity.IncludeUrsula;
import com.nulink.livingratio.entity.StakingRewardLeaderboard;
import com.nulink.livingratio.repository.IncludeUrsulaRepository;
import com.nulink.livingratio.utils.RedisService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.concurrent.TimeUnit;

@Log4j2
@Service
public class IncludeUrsulaService {

    private final static String URSULA_KEY = "URSULA_KEY";

    private final IncludeUrsulaRepository includeUrsulaRepository;

    private final RedisService redisService;

    public IncludeUrsulaService(IncludeUrsulaRepository includeUrsulaRepository, RedisService redisService) {
        this.includeUrsulaRepository = includeUrsulaRepository;
        this.redisService = redisService;
    }

    @Transactional
    public void setIncludeUrsula(int ursula){
        try{
            IncludeUrsula includeUrsula = includeUrsulaRepository.findByUrsulaKey(URSULA_KEY);
            if (includeUrsula == null) {
                includeUrsula = new IncludeUrsula();
                includeUrsula.setUrsulaKey(URSULA_KEY);
                includeUrsula.setUrsulaValue(String.valueOf(ursula));
            } else {
                includeUrsula.setUrsulaValue(String.valueOf(ursula));
            }
            includeUrsulaRepository.save(includeUrsula);
        } catch (Exception e){
            log.error("Save the number of ursula fail" + e);
        }
    }

    public String getUrsulaNum(){
        String getUrsulaNumCacheKey = "UrsulaNum";
        try {
            Object value = redisService.get(getUrsulaNumCacheKey);
            if (value != null) {
                String v = value.toString();
                IncludeUrsula includeUrsula = JSONObject.parseObject(v, IncludeUrsula.class);
                return includeUrsula.getUrsulaValue();
            }
        } catch (Exception e) {
            log.error("getUrsulaNum findByStakingProvider redis read error: {}", e.getMessage());
        }
        IncludeUrsula includeUrsula = includeUrsulaRepository.findByUrsulaKey(URSULA_KEY);
        if (includeUrsula != null){
            try {
                String pvoStr = JSON.toJSONString(includeUrsula, SerializerFeature.WriteNullStringAsEmpty);
                redisService.set(getUrsulaNumCacheKey, pvoStr, 1, TimeUnit.HOURS);
            }catch (Exception e){
                log.error("getUrsulaNum redis write error：{}", e.getMessage());
            }
            return  includeUrsula.getUrsulaValue();
        } else {
            return String.valueOf(0);
        }
    }
}
