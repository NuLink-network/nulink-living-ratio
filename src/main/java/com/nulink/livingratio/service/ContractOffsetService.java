package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.ContractOffset;
import com.nulink.livingratio.repository.ContractOffsetRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
public class ContractOffsetService {

    private final ContractOffsetRepository contractOffsetRepository;

    public ContractOffsetService(ContractOffsetRepository contractOffsetRepository) {
        this.contractOffsetRepository = contractOffsetRepository;
    }

    public ContractOffset findByContractAddress(String contractAddress){
        return contractOffsetRepository.findByContractAddress(contractAddress);
    }

    @Transactional
    public void update(ContractOffset contractOffset){
        contractOffsetRepository.save(contractOffset);
    }
}
