package com.nulink.livingratio.controller;

import com.nulink.livingratio.entity.StakeReward;
import com.nulink.livingratio.service.FaucetNLKService;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "Faucet NLK")
@RestController
@RequestMapping("FaucetNLK")
public class FaucetNLKController {

    private final FaucetNLKService faucetNLKService;

    public FaucetNLKController(FaucetNLKService faucetNLKService) {
        this.faucetNLKService = faucetNLKService;
    }

    @ApiOperation("count by country")
    @GetMapping("countByCountry")
    private BaseResponse<String> countByCountry(@RequestParam(value = "countryCode") String countryCode){
        return BaseResponse.success(faucetNLKService.countByCountry(countryCode));
    }

    @ApiOperation("count by user")
    @GetMapping("countByUser")
    private BaseResponse<String> countByUser(@RequestParam(value = "user") String user){
        return BaseResponse.success(faucetNLKService.countByUser(user));
    }
}
