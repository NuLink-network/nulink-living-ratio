package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.StakeReward;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StakeRewardRepository extends PagingAndSortingRepository<StakeReward, Long>, JpaSpecificationExecutor {

    List<StakeReward> findAllByEpochOrderByCreateTime(String epoch);

    StakeReward findByEpochAndStakingProvider(String epoch, String stakingProvider);

    List<StakeReward> findAllByEpoch(String epoch);

    List<StakeReward> findAllByStakingProviderAndEpochNot(String stakingProvider, String epoch);

    StakeReward findFirstByStakingProviderAndIpAddressIsNotNullOrderByCreateTimeDesc(String stakingProvider);

}
