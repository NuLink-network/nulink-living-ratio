package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.FaucetNLK;
import com.nulink.livingratio.repository.FaucetNLKRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
public class FaucetNLKService {

    private final FaucetNLKRepository faucetNLKRepository;


    public FaucetNLKService(FaucetNLKRepository faucetNLKRepository) {
        this.faucetNLKRepository = faucetNLKRepository;
    }

    @Transactional
    public void create(FaucetNLK faucetNLK){
        FaucetNLK f = faucetNLKRepository.findByTxHash(faucetNLK.getTxHash());
        if (null == f){
            faucetNLKRepository.save(faucetNLK);
        }
    }

    public String countByCountry(String countByCountry){
        long count = faucetNLKRepository.countAllByCountryCode(countByCountry);
        return String.valueOf(count * 10);
    }

    public String countByUser(String user){
        long count = faucetNLKRepository.countAllByUser(user);
        return String.valueOf(count * 10);
    }
}
