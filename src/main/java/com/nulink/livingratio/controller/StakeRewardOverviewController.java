package com.nulink.livingratio.controller;

import com.nulink.livingratio.entity.StakeRewardOverview;
import com.nulink.livingratio.service.StakeRewardOverviewService;
import com.nulink.livingratio.service.StakeService;
import com.nulink.livingratio.utils.Web3jUtils;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@Api(tags = "Stake Reward Overview")
@RestController
@RequestMapping("stakeRewardOverview")
@ConditionalOnProperty(value = "controller.enabled", havingValue = "true")
public class StakeRewardOverviewController {

    private final StakeRewardOverviewService stakeRewardOverviewService;

    private final StakeService stakeService;

    private final Web3jUtils web3jUtils;

    public StakeRewardOverviewController(StakeRewardOverviewService stakeRewardOverviewService,
                                         StakeService stakeService,
                                         Web3jUtils web3jUtils) {
        this.stakeRewardOverviewService = stakeRewardOverviewService;
        this.stakeService = stakeService;
        this.web3jUtils = web3jUtils;
    }

    @GetMapping
    public BaseResponse<StakeRewardOverview> findByEpoch(@RequestParam(value = "epoch") String epoch){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        if (currentEpoch.equals(epoch)){
            StakeRewardOverview stakeRewardOverview = stakeRewardOverviewService.findCurrentEpoch();
            return BaseResponse.success(stakeRewardOverview);
        } else {
            return BaseResponse.success(stakeRewardOverviewService.findLastEpoch(epoch));
        }
    }

    @ApiOperation(value = "find total Stake Reward ")
    @GetMapping("total")
    public BaseResponse findStakingAmount(){
        return BaseResponse.success(stakeRewardOverviewService.findTotal());
    }

    @ApiOperation(value = "total staking node")
    @GetMapping("totalStakingNode")
    public BaseResponse<String> totalStakingNode(){
        return BaseResponse.success(stakeService.totalStakingNode());
    }
}
