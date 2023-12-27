package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.Claim;
import com.nulink.livingratio.repository.ClaimRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;

    public ClaimService(ClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    @Transactional
    public void create(Claim claim){
        Claim byTxHash = claimRepository.findByTxHash(claim.getTxHash());
        if (byTxHash == null) {
            claimRepository.save(claim);
        }
    }
}
