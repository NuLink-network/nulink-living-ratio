package com.nulink.livingratio.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;


@Configuration
public class ProfileConfig {

    @Resource
    private ApplicationContext applicationContext;

    public String getActiveProfile(){
        return applicationContext.getEnvironment().getActiveProfiles()[0];
    }
}
