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
}
