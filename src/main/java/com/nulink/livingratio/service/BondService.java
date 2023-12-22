package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.Bond;
import com.nulink.livingratio.repository.BondRepository;
import com.nulink.livingratio.utils.Web3jUtils;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
public class BondService {

    private final BondRepository bondRepository;
    private final Web3jUtils web3jUtils;

    public BondService(BondRepository bondRepository, Web3jUtils web3jUtils) {
        this.bondRepository = bondRepository;
        this.web3jUtils = web3jUtils;
    }

    @Transactional
    public void create(Bond bond){
        Bond b = bondRepository.findByTxHash(bond.getTxHash());
        if (null != b){
            return;
        }
        bond.setEpoch(web3jUtils.getCurrentEpoch());
        bondRepository.save(bond);
    }

}
