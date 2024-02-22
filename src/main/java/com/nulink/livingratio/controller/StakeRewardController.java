package com.nulink.livingratio.controller;

import com.nulink.livingratio.entity.StakeReward;
import com.nulink.livingratio.service.StakeRewardService;
import com.nulink.livingratio.utils.Web3jUtils;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "Stake Reward")
@RestController
@RequestMapping("stakeReward")
@ConditionalOnProperty(value = "controller.enabled", havingValue = "true")
public class StakeRewardController {

    private final StakeRewardService stakeRewardService;

    private final Web3jUtils web3jUtils;

    public StakeRewardController(StakeRewardService stakeRewardService, Web3jUtils web3jUtils) {
        this.stakeRewardService = stakeRewardService;
        this.web3jUtils = web3jUtils;
    }

    @ApiOperation("node info")
    @GetMapping("/nodeInfo")
    public BaseResponse<StakeReward> nodeInfo(@RequestParam(value = "stakeProvider") String stakeProvider){
        return BaseResponse.success(stakeRewardService.nodeInfo(stakeProvider));
    }

    @ApiOperation("Stake Reward Info")
    @GetMapping("/findOne")
    public BaseResponse<StakeReward> findStakeReward(@RequestParam(value = "stakeProvider") String stakeProvider,
                                                     @RequestParam(value = "epoch") String epoch){
        return BaseResponse.success(stakeRewardService.findByEpochAndStakingProvider(stakeProvider, epoch));
    }

    @ApiOperation(value = "Stake Reward Page")
    @GetMapping("page")
    public BaseResponse<Page<StakeReward>> findStakeRewardPage(@RequestParam(value = "epoch") String epoch,
                                                               @RequestParam(value = "orderBy", required = false) String orderBy,
                                                               @RequestParam(value = "sorted", required = false) String sorted,
                                                               @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                               @RequestParam(value = "pageNum", defaultValue = "1") int pageNum){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        if (currentEpoch.equals(epoch)){
            return BaseResponse.success(stakeRewardService.findCurrentEpochPage(pageSize, pageNum, orderBy, sorted));
        }
        return BaseResponse.success(stakeRewardService.findPage(epoch, pageSize, pageNum, orderBy, sorted));
    }

    @ApiOperation(value = "Stake Reward list")
    @GetMapping("list/{epoch}")
    public BaseResponse<List<StakeReward>> list(@PathVariable String epoch){
        return BaseResponse.success(stakeRewardService.list(epoch));
    }

    @ApiOperation(value = "user total staking reward")
    @GetMapping("userTotalStakingReward")
    public BaseResponse<String> userTotalStakingReward(@RequestParam(value = "address") String address){
        return BaseResponse.success(stakeRewardService.userTotalStakingReward(address));
    }

    @ApiOperation(value = "check allOnline at least One Epoch")
    @GetMapping("checkAllOnlineAtLeastOneEpoch/{stakingProvider}")
    public BaseResponse<Boolean> checkAllOnlineWithinOneEpoch(@PathVariable String stakingProvider){
        return BaseResponse.success(stakeRewardService.checkAllOnlineWithinOneEpoch(stakingProvider));
    }

}
