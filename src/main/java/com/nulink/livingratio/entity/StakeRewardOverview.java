package com.nulink.livingratio.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "stake_reward_overview")
public class StakeRewardOverview extends BaseEntity{

    @Column(name = "valid_staking_amount")
    private String validStakingAmount;

    @Column(name = "total_staking_amount")
    private String totalStakingAmount;

    @Column(name = "current_epoch_reward")
    private String currentEpochReward;

    @Column(name = "accumulated_reward")
    private String accumulatedReward;

    @Column(name = "total_staking_nodes")
    private String totalStakingNodes;

    @Column(name = "epoch")
    private String epoch;

    public String getValidStakingAmount() {
        return validStakingAmount;
    }

    public void setValidStakingAmount(String validStakingAmount) {
        this.validStakingAmount = validStakingAmount;
    }

    public String getTotalStakingAmount() {
        return totalStakingAmount;
    }

    public void setTotalStakingAmount(String totalStakingAmount) {
        this.totalStakingAmount = totalStakingAmount;
    }

    public String getCurrentEpochReward() {
        return currentEpochReward;
    }

    public void setCurrentEpochReward(String currentEpochReward) {
        this.currentEpochReward = currentEpochReward;
    }

    public String getAccumulatedReward() {
        return accumulatedReward;
    }

    public void setAccumulatedReward(String accumulatedReward) {
        this.accumulatedReward = accumulatedReward;
    }

    public String getTotalStakingNodes() {
        return totalStakingNodes;
    }

    public void setTotalStakingNodes(String totalStakingNodes) {
        this.totalStakingNodes = totalStakingNodes;
    }

    public String getEpoch() {
        return epoch;
    }

    public void setEpoch(String epoch) {
        this.epoch = epoch;
    }
}
