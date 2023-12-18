package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.StakeRewardOverview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StakeRewardOverviewRepository extends JpaRepository<StakeRewardOverview, Long>, JpaSpecificationExecutor {

    StakeRewardOverview findByEpoch(String epoch);

    @Query(value = "select * from stake_reward_overview where epoch < :epoch", nativeQuery = true)
    List<StakeRewardOverview> findAllByEpochBefore(int epoch);
}
