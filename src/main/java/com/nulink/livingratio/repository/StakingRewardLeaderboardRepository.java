package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.StakingRewardLeaderboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface StakingRewardLeaderboardRepository extends PagingAndSortingRepository<StakingRewardLeaderboard, Long>, JpaRepository<StakingRewardLeaderboard, Long> {
    int countByEpoch(String epoch);

    StakingRewardLeaderboard findByStakingProvider(String stakingProvider);

}
