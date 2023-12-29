package com.nulink.livingratio.repository;

import com.nulink.livingratio.entity.Stake;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

@Repository
public interface StakeRepository extends PagingAndSortingRepository<Stake, Long>, JpaSpecificationExecutor {

    Stake findByTxHash(String txHash);

    @Query(value = "SELECT\n" +
                    "	s.* \n" +
                    "FROM\n" +
                    "	stake s,\n" +
                    "	( SELECT max( create_time ) create_time, USER FROM stake WHERE create_time <= :epochStartTime GROUP BY USER ) t \n" +
                    "WHERE\n" +
                    "	s.USER = t.USER \n" +
                    "	AND s.create_time = t.create_time \n" +
                    "	AND s.`event` = 'stake'", nativeQuery = true)
    List<Stake> findValidStakeByEpoch(@Param("epochStartTime") Timestamp epochStartTime);

    @Query(value = "SELECT\n" +
                    "	s.* \n" +
                    "FROM\n" +
                    "	stake s,\n" +
                    "	( SELECT max( create_time ) create_time, USER FROM stake GROUP BY USER ) t \n" +
                    "WHERE\n" +
                    "	s.USER = t.USER \n" +
                    "	AND s.create_time = t.create_time ", nativeQuery = true)
    List<Stake> findLatest();

    Stake findFirstByUserAndEventOrderByCreateTimeDesc(String user, String event);

    Stake findFirstByUserAndEventAndCreateTimeBeforeOrderByCreateTimeDesc(String user, String event, Timestamp createTIme);

    List<Stake> findAllByUserAndEventAndCreateTimeBetween(String user, String event, Date startTime, Timestamp endTime);

    List<Stake> findAllByUser(String user);

    List<Stake> findAll();
}
