package com.nulink.livingratio.controller;

import com.nulink.livingratio.entity.SetLivingRatio;
import com.nulink.livingratio.service.SetLivingRatioService;
import com.nulink.livingratio.utils.Web3jUtils;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "Health")
@RestController
@RequestMapping("health")
public class HealthController {

    private final Web3jUtils web3jUtils;

    private final SetLivingRatioService setLivingRatioService;

    public HealthController(Web3jUtils web3jUtils, SetLivingRatioService setLivingRatioService) {
        this.web3jUtils = web3jUtils;
        this.setLivingRatioService = setLivingRatioService;
    }

    @GetMapping
    public ResponseEntity health(){
        return new ResponseEntity<>(BaseResponse.success("Staking Service is Running"), HttpStatus.OK);
    }

    @GetMapping("stakingReward")
    public ResponseEntity checkStakingReward(){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        String startTime = web3jUtils.getEpochStartTime(currentEpoch);
        if (System.currentTimeMillis() < (Long.parseLong(startTime) * 1000 + 15 * 60 * 1000)){
            SetLivingRatio livingRatio = setLivingRatioService.findByEpoch(currentEpoch);
            if (livingRatio.isSetLivingRatio()){
                return new ResponseEntity<>(BaseResponse.success(livingRatio), HttpStatus.OK);
            } else {
                return new ResponseEntity<>(BaseResponse.failed("Set living ratio task is failed", null), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return new ResponseEntity<>(BaseResponse.success("Set living ratio task is currently being executed ..."), HttpStatus.OK);
    }
}
