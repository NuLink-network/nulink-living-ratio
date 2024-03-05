package com.nulink.livingratio.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "leaderboard_blacklist")
public class LeaderboardBlacklist extends BaseEntity {

    @Column(name = "staking_provider")
    private String stakingProvider;

    @Column(name = "is_deleted", columnDefinition = "bit(1) DEFAULT b'0'")
    private boolean deleted = false;

    public String getStakingProvider() {
        return stakingProvider;
    }

    public void setStakingProvider(String stakingProvider) {
        this.stakingProvider = stakingProvider;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
