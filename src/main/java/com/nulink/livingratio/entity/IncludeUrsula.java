package com.nulink.livingratio.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "include_ursula")
public class IncludeUrsula extends BaseEntity{

    @Column(name = "ursula_key")
    private String ursulaKey;

    @Column(name = "ursula_value")
    private String ursulaValue;

    public String getUrsulaKey() {
        return ursulaKey;
    }

    public void setUrsulaKey(String ursulaKey) {
        this.ursulaKey = ursulaKey;
    }

    public String getUrsulaValue() {
        return ursulaValue;
    }

    public void setUrsulaValue(String ursulaValue) {
        this.ursulaValue = ursulaValue;
    }
}
