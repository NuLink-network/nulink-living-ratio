package com.nulink.livingratio.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.ColumnDefault;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "stake_reward")
public class StakeReward extends BaseEntity{

    @Column(name = "staking_provider")
    private String stakingProvider;

    @Column(name = "operator")
    private String operator;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "staking_amount")
    private String stakingAmount;

    @Column(name = "living_ratio")
    @ColumnDefault("0")
    private String livingRatio;

    @Column(name = "valid_staking_amount")
    @ColumnDefault("0")
    private String validStakingAmount;

    @Column(name = "staking_reward")
    private String stakingReward;

    @Column(name = "valid_staking_quota")
    private String validStakingQuota;

    @Column(name = "connectable")
    @ColumnDefault("0")
    private int connectable = 0;

    @Column(name = "connect_fail")
    @ColumnDefault("0")
    private int connectFail = 0;

    @Column(name = "unstake")
    @ColumnDefault("0")
    private int unStake = 0;

    @Column(name = "ping_count")
    @ColumnDefault("0")
    private int pingCount = 0;

    @Column(name = "epoch")
    private String epoch;

    @Transient
    private boolean online;


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

    public int getConnectable() {
        return connectable;
    }

    public void setConnectable(int connectable) {
        this.connectable = connectable;
    }

    public int getConnectFail() {
        return connectFail;
    }

    public void setConnectFail(int connectFail) {
        this.connectFail = connectFail;
    }

    public int getUnStake() {
        return unStake;
    }

    public void setUnStake(int unStake) {
        this.unStake = unStake;
    }

    public int getPingCount() {
        return pingCount;
    }

    public void setPingCount(int pingCount) {
        this.pingCount = pingCount;
    }

    public String getEpoch() {
        return epoch;
    }

    public void setEpoch(String epoch) {
        this.epoch = epoch;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}
