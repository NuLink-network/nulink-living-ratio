package com.nulink.livingratio.service;

import com.nulink.livingratio.entity.IncludeUrsula;
import com.nulink.livingratio.repository.IncludeUrsulaRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Log4j2
@Service
public class IncludeUrsulaService {

    private final static String URSULA_KEY = "URSULA_KEY";

    private final IncludeUrsulaRepository includeUrsulaRepository;

    public IncludeUrsulaService(IncludeUrsulaRepository includeUrsulaRepository) {
        this.includeUrsulaRepository = includeUrsulaRepository;
    }

    @Transactional
    public void setIncludeUrsula(int ursula){
        try{
            IncludeUrsula includeUrsula = includeUrsulaRepository.findByUrsulaKey(URSULA_KEY);
            if (includeUrsula == null) {
                includeUrsula = new IncludeUrsula();
                includeUrsula.setUrsulaKey(URSULA_KEY);
                includeUrsula.setUrsulaValue(String.valueOf(ursula));
            } else {
                includeUrsula.setUrsulaValue(String.valueOf(ursula));
            }
            includeUrsulaRepository.save(includeUrsula);
        } catch (Exception e){
            log.error("Save the number of ursula fail" + e);
        }
    }

    public String getUrsulaNum(){
        IncludeUrsula includeUrsula = includeUrsulaRepository.findByUrsulaKey(URSULA_KEY);
        if (includeUrsula != null){
            return  includeUrsula.getUrsulaValue();
        } else {
            return String.valueOf(0);
        }
    }
}
