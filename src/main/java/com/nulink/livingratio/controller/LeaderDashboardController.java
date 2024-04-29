package com.nulink.livingratio.controller;

import com.nulink.livingratio.entity.StakingRewardLeaderboard;
import com.nulink.livingratio.service.StakingRewardLeaderboardService;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Api(tags = "Leader Dashboard")
@RestController
@RequestMapping("leaderDashboard")
@ConditionalOnProperty(value = "controller.enabled", havingValue = "true")
public class LeaderDashboardController {

    private final StakingRewardLeaderboardService stakingRewardLeaderboardService;

    public LeaderDashboardController(StakingRewardLeaderboardService stakingRewardLeaderboardService) {
        this.stakingRewardLeaderboardService = stakingRewardLeaderboardService;
    }

    @ApiOperation("find By StakingProvider")
    @GetMapping("/{stakingProvider}")
    public BaseResponse<StakingRewardLeaderboard> findByStakingProvider(@PathVariable String stakingProvider){
        return BaseResponse.success(stakingRewardLeaderboardService.findByStakingProvider(stakingProvider));
    }

    @ApiOperation("find By page")
    @GetMapping("/findByPage")
    public BaseResponse<Page<StakingRewardLeaderboard>> findByPage(@RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                                  @RequestParam(value = "pageNum", defaultValue = "1") int pageNum){
        return BaseResponse.success(stakingRewardLeaderboardService.findByPage(pageSize, pageNum));
    }
}
