package com.nulink.livingratio.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "set_living_ratio")
public class SetLivingRatio extends BaseEntity{

    @Column(name = "tx_hash")
    private String txHash;

    @Column(name = "epoch", unique = true)
    private String epoch;

    @Column(name = "set_living_ratio")
    private boolean setLivingRatio;

    @Column(name = "transaction_fail", columnDefinition = "bit(1) DEFAULT b'0'")
    private boolean transactionFail;

    @Column(name = "reason",  columnDefinition = " text")
    private String reason;

    public String getTxHash() {
        return txHash;
    }

    public void setTxHash(String txHash) {
        this.txHash = txHash;
    }

    public String getEpoch() {
        return epoch;
    }

    public void setEpoch(String epoch) {
        this.epoch = epoch;
    }

    public boolean isSetLivingRatio() {
        return setLivingRatio;
    }

    public void setSetLivingRatio(boolean setLivingRatio) {
        this.setLivingRatio = setLivingRatio;
    }

    public boolean isTransactionFail() {
        return transactionFail;
    }

    public void setTransactionFail(boolean transactionFail) {
        this.transactionFail = transactionFail;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
