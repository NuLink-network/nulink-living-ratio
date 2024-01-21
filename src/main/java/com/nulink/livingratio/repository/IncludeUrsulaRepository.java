package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.Bond;
import com.nulink.livingratio.entity.IncludeUrsula;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncludeUrsulaRepository extends PagingAndSortingRepository<IncludeUrsula, Long>, JpaSpecificationExecutor {

    IncludeUrsula findByKey(String key);

}
