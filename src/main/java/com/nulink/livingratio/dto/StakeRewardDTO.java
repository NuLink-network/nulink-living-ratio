package com.nulink.livingratio.dto;

public class StakeRewardDTO extends BaseDto {

    private String stakingProvider;

    private String operator;

    private String ipAddress;

    private String stakingAmount;

    private String livingRatio;

    private String validStakingAmount;

    private String stakingReward;

    private String validStakingQuota;

    public String getStakingProvider() {
        return stakingProvider;
    }

    public void setStakingProvider(String stakingProvider) {
        this.stakingProvider = stakingProvider;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getStakingAmount() {
        return stakingAmount;
    }

    public void setStakingAmount(String stakingAmount) {
        this.stakingAmount = stakingAmount;
    }

    public String getLivingRatio() {
        return livingRatio;
    }

    public void setLivingRatio(String livingRatio) {
        this.livingRatio = livingRatio;
    }

    public String getValidStakingAmount() {
        return validStakingAmount;
    }

    public void setValidStakingAmount(String validStakingAmount) {
        this.validStakingAmount = validStakingAmount;
    }

    public String getStakingReward() {
        return stakingReward;
    }

    public void setStakingReward(String stakingReward) {
        this.stakingReward = stakingReward;
    }

    public String getValidStakingQuota() {
        return validStakingQuota;
    }

    public void setValidStakingQuota(String validStakingQuota) {
        this.validStakingQuota = validStakingQuota;
    }
}
