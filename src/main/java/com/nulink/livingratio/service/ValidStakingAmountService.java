package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.Stake;
import com.nulink.livingratio.entity.ValidStakingAmount;
import com.nulink.livingratio.repository.ValidStakingAmountRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ValidStakingAmountService {

    private static String STAKE_EVENT = "stake";

    private static String UN_STAKE_EVENT = "unstake";

    private final ValidStakingAmountRepository validStakingAmountRepository;

    public ValidStakingAmountService(ValidStakingAmountRepository validStakingAmountRepository) {
        this.validStakingAmountRepository = validStakingAmountRepository;
    }

    @Transactional
    public void updateValidStakingAmount(Stake stake){
        String stakeUser = stake.getUser();
        String epoch =  stake.getEpoch();
        ValidStakingAmount validStakingAmount = validStakingAmountRepository.findFirstByStakingProviderOrderByCreateTimeDesc(stakeUser);
        if (null != validStakingAmount){
            String stakingAmountEpoch = String.valueOf(validStakingAmount.getEpoch());
            if (epoch.equalsIgnoreCase(stakingAmountEpoch)){
                if (STAKE_EVENT.equalsIgnoreCase(stake.getEvent())){
                    validStakingAmount.setStakingAmount(new BigDecimal(validStakingAmount.getStakingAmount()).add(new BigDecimal(stake.getAmount())).toString());
                }
                if (UN_STAKE_EVENT.equalsIgnoreCase(stake.getEvent())){
                    validStakingAmount.setStakingAmount("0");
                }
                validStakingAmountRepository.save(validStakingAmount);
            } else {
                ValidStakingAmount newEpochStakingAmount = new ValidStakingAmount();
                newEpochStakingAmount.setStakingProvider(stakeUser);
                newEpochStakingAmount.setEpoch(Integer.parseInt(epoch));
                if (STAKE_EVENT.equalsIgnoreCase(stake.getEvent())){
                    newEpochStakingAmount.setStakingAmount(new BigDecimal(validStakingAmount.getStakingAmount()).add(new BigDecimal(stake.getAmount())).toString());
                }
                if (UN_STAKE_EVENT.equalsIgnoreCase(stake.getEvent())){
                    newEpochStakingAmount.setStakingAmount("0");
                }
                validStakingAmountRepository.save(newEpochStakingAmount);
            }
        } else {
            ValidStakingAmount stakingAmount = new ValidStakingAmount();
            stakingAmount.setStakingProvider(stakeUser);
            stakingAmount.setEpoch(Integer.parseInt(epoch));
            if (STAKE_EVENT.equalsIgnoreCase(stake.getEvent())){
                stakingAmount.setStakingAmount(stake.getAmount());
            }
            if (UN_STAKE_EVENT.equalsIgnoreCase(stake.getEvent())){
                stakingAmount.setStakingAmount("0");
            }
            validStakingAmountRepository.save(stakingAmount);
        }
    }

    public Map<String, String> findValidStakingAmount(int epoch){
        List<ValidStakingAmount> validStakingAmounts =  validStakingAmountRepository.findNewestValidStakingAmountBeforeEpoch(epoch);
        Map<String, String> validStakingAmountMap = new HashMap<>();
        for (ValidStakingAmount validStakingAmount : validStakingAmounts) {
            validStakingAmountMap.put(validStakingAmount.getStakingProvider(), validStakingAmount.getStakingAmount());
        }
        return validStakingAmountMap;
    }
}
