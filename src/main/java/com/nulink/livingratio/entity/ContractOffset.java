package com.nulink.livingratio.entity;

import javax.persistence.*;
import java.math.BigInteger;
import java.sql.Timestamp;

@Entity
@Table(name = "contract_offset")
public class ContractOffset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected long id;
    private String contractAddress;
    private String contractName;
    @Column(name = "block_offset", columnDefinition = "decimal(19,0)", nullable = false)
    private BigInteger blockOffset;
    private Timestamp recordedAt;

    public ContractOffset() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public String getContractName() {
        return contractName;
    }

    public void setContractName(String contractName) {
        this.contractName = contractName;
    }

    public BigInteger getBlockOffset() {
        return blockOffset;
    }

    public void setBlockOffset(BigInteger blockOffset) {
        this.blockOffset = blockOffset;
    }

    public Timestamp getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Timestamp recordedAt) {
        this.recordedAt = recordedAt;
    }


    public ContractOffset(String contractAddress) {
        this.contractAddress = contractAddress;
    }
}
