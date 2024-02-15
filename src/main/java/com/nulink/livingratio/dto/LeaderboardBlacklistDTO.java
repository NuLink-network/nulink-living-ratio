package com.nulink.livingratio.dto;


import javax.validation.constraints.NotBlank;

public class LeaderboardBlacklistDTO extends BaseDto{

    @NotBlank
    private String stakingProvider;

    public String getStakingProvider() {
        return stakingProvider;
    }

    public void setStakingProvider(String stakingProvider) {
        this.stakingProvider = stakingProvider;
    }
}
