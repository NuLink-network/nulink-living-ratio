package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.StakeReward;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StakeRewardRepository extends PagingAndSortingRepository<StakeReward, Long>, JpaSpecificationExecutor {

    StakeReward findByEpochAndStakingProvider(String epoch, String stakingProvider);

    List<StakeReward> findAllByEpoch(String epoch);

    Page<StakeReward> findAllByEpoch(String epoch, Pageable pageable);

    List<StakeReward> findAllByEpochAndLivingRatioNot(String epoch, String stakingReward);

    List<StakeReward> findAllByStakingProvider(String StakingProvider);

    List<StakeReward> findAllByStakingProviderAndEpochNot(String stakingProvider, String epoch);

    @Query(value = "SELECT count(1) FROM stake_reward sr where staking_provider = :stakingProvider and SUBSTR(sr.living_ratio, 1, 1) = '1' and epoch != :currentEpoch", nativeQuery = true)
    int countStakingProviderAllOnlineEpoch(@Param("stakingProvider") String stakingProvider, @Param("currentEpoch") String currentEpoch);

}
