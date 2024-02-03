package com.nulink.livingratio.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "stake_reward_leaderboard")
public class StakingRewardLeaderboard extends BaseEntity{

    @Column(name = "staking_provider")
    private String stakingProvider;

    @Column(name = "accumulated_staking_reward")
    private String accumulatedStakingReward;

    @Column(name = "ranking")
    private int ranking;

    @Column(name = "epoch")
    private String epoch;

    public String getStakingProvider() {
        return stakingProvider;
    }

    public void setStakingProvider(String stakingProvider) {
        this.stakingProvider = stakingProvider;
    }

    public String getAccumulatedStakingReward() {
        return accumulatedStakingReward;
    }

    public void setAccumulatedStakingReward(String accumulatedStakingReward) {
        this.accumulatedStakingReward = accumulatedStakingReward;
    }

    public int getRanking() {
        return ranking;
    }

    public void setRanking(int ranking) {
        this.ranking = ranking;
    }

    public String getEpoch() {
        return epoch;
    }

    public void setEpoch(String epoch) {
        this.epoch = epoch;
    }
}
