package com.nulink.livingratio.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "faucet_nlk")
public class FaucetNLK extends BaseEntity{

    @Column(name = "tx_hash", nullable = false, unique = true)
    private String txHash;

    @Column(name = "user")
    private String user;

    @Column(name = "country_code")
    private String countryCode;

    @Column(name = "ip_address")
    private String ipAddress;

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

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
