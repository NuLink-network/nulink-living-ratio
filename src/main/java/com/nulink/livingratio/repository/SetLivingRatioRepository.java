package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.SetLivingRatio;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SetLivingRatioRepository extends PagingAndSortingRepository<SetLivingRatio, Long>, JpaSpecificationExecutor {

    SetLivingRatio findFirstBySetLivingRatioOrderByCreateTime(boolean set);

}


