package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.LeaderboardBlacklist;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaderboardBlacklistRepository extends JpaRepository<LeaderboardBlacklist, Long>, JpaSpecificationExecutor {

    @Modifying
    void deleteByStakingProvider(String stakingProvider);

    LeaderboardBlacklist findByStakingProvider(String stakingProvider);

    List<LeaderboardBlacklist> findAllByDeleted(Boolean deleted);
}
