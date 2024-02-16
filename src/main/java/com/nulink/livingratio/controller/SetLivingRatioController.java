package com.nulink.livingratio.controller;

import com.nulink.livingratio.entity.SetLivingRatio;
import com.nulink.livingratio.service.SetLivingRatioService;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api(tags = "Living Ratio")
@RestController
@RequestMapping("SetLivingRatio")
@ConditionalOnProperty(value = "controller.enabled", havingValue = "true")
public class SetLivingRatioController {

    private final SetLivingRatioService setLivingRatioService;

    public SetLivingRatioController(SetLivingRatioService setLivingRatioService) {
        this.setLivingRatioService = setLivingRatioService;
    }

    @ApiOperation("find un set")
    @GetMapping("findUnset")
    public BaseResponse<List<SetLivingRatio>> findUnset(){
        return BaseResponse.success(setLivingRatioService.findUnset());
    }
}
