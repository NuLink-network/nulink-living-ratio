package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.Bond;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BondRepository extends PagingAndSortingRepository<Bond, Long>, JpaSpecificationExecutor {

    List<Bond> findAllByEpoch(String epoch);

    @Query(value = "SELECT b.* \n" +
                    "FROM bond b,\n" +
                    "( SELECT staking_provider, max( create_time ) create_time FROM bond WHERE operator IS NOT NULL GROUP BY staking_provider ) t \n" +
                    "WHERE\n" +
                    "	b.staking_provider = t.staking_provider \n" +
                    "	AND b.create_time = t.create_time", nativeQuery = true)
    List<Bond> findLatestBond();

    @Query(value = "SELECT b.* \n" +
            "FROM bond b,\n" +
            "( SELECT staking_provider, max( create_time ) create_time FROM bond GROUP BY staking_provider ) t \n" +
            "WHERE\n" +
            "	b.staking_provider = t.staking_provider \n" +
            "	AND b.create_time = t.create_time", nativeQuery = true)
    List<Bond> findLatest();

    Bond findTop1ByStakingProviderAndOperatorIsNotNullOrderByCreateTimeDesc(String stakingProvider);
}
