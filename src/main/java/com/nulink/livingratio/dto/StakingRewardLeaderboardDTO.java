package com.nulink.livingratio.dto;

public class StakingRewardLeaderboardDTO {

    private String stakingProvider;

    private String stakingRewardAmount;

    private String index;

    public StakingRewardLeaderboardDTO() {
    }

    public StakingRewardLeaderboardDTO(String stakingProvider, String stakingRewardAmount, String index) {
        this.stakingProvider = stakingProvider;
        this.stakingRewardAmount = stakingRewardAmount;
        this.index = index;
    }

    public String getStakingProvider() {
        return stakingProvider;
    }

    public void setStakingProvider(String stakingProvider) {
        this.stakingProvider = stakingProvider;
    }

    public String getStakingRewardAmount() {
        return stakingRewardAmount;
    }

    public void setStakingRewardAmount(String stakingRewardAmount) {
        this.stakingRewardAmount = stakingRewardAmount;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }
}
