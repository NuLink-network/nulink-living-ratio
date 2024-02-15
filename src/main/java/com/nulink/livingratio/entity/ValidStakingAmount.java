package com.nulink.livingratio.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "valid_staking_amount")
public class ValidStakingAmount extends BaseEntity{

    @Column(name = "epoch")
    private int epoch;

    @Column(name = "staking_provider")
    private String stakingProvider;

    @Column(name = "staking_amount")
    private String stakingAmount;

    public int getEpoch() {
        return epoch;
    }

    public void setEpoch(int epoch) {
        this.epoch = epoch;
    }

    public String getStakingProvider() {
        return stakingProvider;
    }

    public void setStakingProvider(String stakingProvider) {
        this.stakingProvider = stakingProvider;
    }

    public String getStakingAmount() {
        return stakingAmount;
    }

    public void setStakingAmount(String stakingAmount) {
        this.stakingAmount = stakingAmount;
    }
}
