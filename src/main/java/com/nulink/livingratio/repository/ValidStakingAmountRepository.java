package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.ValidStakingAmount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidStakingAmountRepository extends JpaRepository<ValidStakingAmount, Long>, JpaSpecificationExecutor {

    ValidStakingAmount findByStakingProviderAndEpoch(String stakingProvider, String epoch);

    ValidStakingAmount findFirstByStakingProviderOrderByCreateTimeDesc(String stakingProvider);

    @Query(value = "SELECT\n" +
            "	v.* \n" +
            "FROM\n" +
            "	valid_staking_amount v,\n" +
            "	( SELECT staking_provider, MAX( id ) AS id FROM valid_staking_amount vsa WHERE vsa.epoch <= :epoch GROUP BY vsa.staking_provider ) t \n" +
            "WHERE\n" +
            "	v.staking_provider = t.staking_provider \n" +
            "	AND v.id = t.id", nativeQuery = true)
    List<ValidStakingAmount> findNewestValidStakingAmountBeforeEpoch(@Param("epoch") int epoch);
}
