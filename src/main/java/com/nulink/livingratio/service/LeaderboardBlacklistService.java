package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.LeaderboardBlacklist;
import com.nulink.livingratio.repository.LeaderboardBlacklistRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

@Log4j2
@Service
public class LeaderboardBlacklistService {

    private final LeaderboardBlacklistRepository leaderboardBlacklistRepository;

    public LeaderboardBlacklistService(LeaderboardBlacklistRepository leaderboardBlacklistRepository) {
        this.leaderboardBlacklistRepository = leaderboardBlacklistRepository;
    }

    @Transactional
    public void create(LeaderboardBlacklist leaderboardBlacklist){
        if (leaderboardBlacklist.getStakingProvider() != null) {
            String lowerCase = leaderboardBlacklist.getStakingProvider().toLowerCase();
            LeaderboardBlacklist blacklist = leaderboardBlacklistRepository.findByStakingProvider(lowerCase);
            if (blacklist == null){
                leaderboardBlacklist.setStakingProvider(lowerCase);
                leaderboardBlacklistRepository.save(leaderboardBlacklist);
            } else {
                throw new RuntimeException("StakingProvider Already exists");
            }
        } else {
            throw new RuntimeException("StakingProvider can not empty");
        }
    }

    public Page<LeaderboardBlacklist> findByPage(int pageNum, int pageSize){
        PageRequest pageRequest = PageRequest.of(pageNum, pageSize);
        return leaderboardBlacklistRepository.findAll(pageRequest);
    }

    public List<LeaderboardBlacklist> findAll(boolean deleted){
        return leaderboardBlacklistRepository.findAllByDeleted(deleted);
    }

    @Transactional
    public void delete(String stakingProvider){
        LeaderboardBlacklist blacklist = leaderboardBlacklistRepository.findByStakingProvider(stakingProvider);
        blacklist.setDeleted(true);
        leaderboardBlacklistRepository.save(blacklist);
    }

    public LeaderboardBlacklist findByStakingProvider(String stakingProvider){
        return leaderboardBlacklistRepository.findByStakingProvider(stakingProvider);
    };

}
