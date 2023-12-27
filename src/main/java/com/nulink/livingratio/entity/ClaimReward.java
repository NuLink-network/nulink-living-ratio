package com.nulink.livingratio.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "claim_reward")
public class ClaimReward extends BaseEntity{

    @Column(name = "tx_hash", nullable = false, unique = true)
    private String txHash;

    @Column(name = "user")
    private String user;

    @Column(name = "reward_amount")
    private String rewardAmount;

    @Column(name = "time")
    private String time;

    @Column(name = "epoch")
    private String lastEpoch;

    public String getTxHash() {
        return txHash;
    }

    public void setTxHash(String txHash) {
        this.txHash = txHash;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getRewardAmount() {
        return rewardAmount;
    }

    public void setRewardAmount(String rewardAmount) {
        this.rewardAmount = rewardAmount;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getLastEpoch() {
        return lastEpoch;
    }

    public void setLastEpoch(String lastEpoch) {
        this.lastEpoch = lastEpoch;
    }
}
