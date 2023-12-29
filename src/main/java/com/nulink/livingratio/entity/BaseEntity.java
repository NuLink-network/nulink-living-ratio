package com.nulink.livingratio.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Date;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected long id;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "create_time", nullable = false, columnDefinition = "timestamp DEFAULT CURRENT_TIMESTAMP")
    private Timestamp createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "last_update_time", nullable = false, columnDefinition = "timestamp DEFAULT CURRENT_TIMESTAMP")
    private Timestamp lastUpdateTime;

    public BaseEntity() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public Timestamp getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Timestamp lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    @PrePersist
    protected void prePersist() {
        if (this.createTime == null) createTime = new Timestamp(new Date().getTime());
        if (this.lastUpdateTime == null) lastUpdateTime = new Timestamp(new Date().getTime());
    }

    @PreUpdate
    protected void preUpdate() {
        this.lastUpdateTime = new Timestamp(new Date().getTime());
    }

}
