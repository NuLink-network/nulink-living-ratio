package com.nulink.livingratio.controller;

import com.nulink.livingratio.service.IncludeUrsulaService;
import com.nulink.livingratio.vo.BaseResponse;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Api(tags = "IncludeUrsula")
@RestController
@RequestMapping("includeUrsula")
public class IncludeUrsulaController {

    private final IncludeUrsulaService includeUrsulaService;

    public IncludeUrsulaController(IncludeUrsulaService includeUrsulaService) {
        this.includeUrsulaService = includeUrsulaService;
    }

    @GetMapping("/getUrsulaNum")
    public BaseResponse<String> getUrsulaNum(){
        return BaseResponse.success(includeUrsulaService.getUrsulaNum());
    }
}
