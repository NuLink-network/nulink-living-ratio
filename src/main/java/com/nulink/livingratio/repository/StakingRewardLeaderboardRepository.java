package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.StakingRewardLeaderboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;


@Repository
public interface StakingRewardLeaderboardRepository extends JpaRepository<StakingRewardLeaderboard, Long>, JpaSpecificationExecutor {
    int countByEpoch(String epoch);

    StakingRewardLeaderboard findByStakingProviderAndRankingNot(String stakingProvider, int ranking);

}
