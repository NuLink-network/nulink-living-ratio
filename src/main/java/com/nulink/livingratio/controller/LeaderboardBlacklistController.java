package com.nulink.livingratio.controller;

import com.nulink.livingratio.dto.LeaderboardBlacklistDTO;
import com.nulink.livingratio.entity.LeaderboardBlacklist;
import com.nulink.livingratio.service.LeaderboardBlacklistService;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Api(tags = "Leaderboard Blacklist")
@RestController
@RequestMapping("leaderboardBlacklist")
@ConditionalOnProperty(value = "controller.enabled", havingValue = "true")
public class LeaderboardBlacklistController {

    private final LeaderboardBlacklistService leaderboardBlacklistService;

    public LeaderboardBlacklistController(LeaderboardBlacklistService leaderboardBlacklistService) {
        this.leaderboardBlacklistService = leaderboardBlacklistService;
    }

    @PostMapping
    public BaseResponse create(@RequestBody LeaderboardBlacklistDTO leaderboardBlacklistDTO){
        LeaderboardBlacklist leaderboardBlacklist = new LeaderboardBlacklist();
        leaderboardBlacklist.setStakingProvider(leaderboardBlacklistDTO.getStakingProvider());
        leaderboardBlacklistService.create(leaderboardBlacklist);
        return BaseResponse.success("success");
    }

    @PostMapping("{stakingProvider}")
    public BaseResponse delete(@PathVariable String stakingProvider){
        leaderboardBlacklistService.delete(stakingProvider);
        return BaseResponse.success("success");
    }

    @GetMapping("/findByStakingProvider/{stakingProvider}")
    public BaseResponse<LeaderboardBlacklist> findByStakingProvider(@PathVariable String stakingProvider){
        return BaseResponse.success(leaderboardBlacklistService.findByStakingProvider(stakingProvider));
    }

    @GetMapping("/findByPage")
    public BaseResponse<Page<LeaderboardBlacklist>> findByPage(@RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                               @RequestParam(value = "pageNum", defaultValue = "1") int pageNum){
        return BaseResponse.success(leaderboardBlacklistService.findByPage(pageNum - 1, pageSize));
    }
}
