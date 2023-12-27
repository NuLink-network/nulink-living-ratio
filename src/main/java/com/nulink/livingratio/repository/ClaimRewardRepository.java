package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.ClaimReward;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimRewardRepository extends PagingAndSortingRepository<ClaimReward, Long>, JpaSpecificationExecutor {

    ClaimReward findByTxHash(String txHash);
}
