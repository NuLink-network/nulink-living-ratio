package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.FaucetNLK;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FaucetNLKRepository extends PagingAndSortingRepository<FaucetNLK, Long>, JpaSpecificationExecutor {

    FaucetNLK findByTxHash(String txHash);

    long countAllByCountryCode(String countryCode);
    long countAllByUser(String user);
}
