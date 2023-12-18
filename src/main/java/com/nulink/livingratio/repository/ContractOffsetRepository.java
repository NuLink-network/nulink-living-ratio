package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.ContractOffset;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractOffsetRepository extends PagingAndSortingRepository<ContractOffset, Long>, JpaSpecificationExecutor {

    ContractOffset findByContractAddress(String contractAddress);
}
