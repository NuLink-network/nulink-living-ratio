package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.Claim;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimRepository extends PagingAndSortingRepository<Claim, Long>, JpaSpecificationExecutor {

    Claim findByTxHash(String txHash);

}
