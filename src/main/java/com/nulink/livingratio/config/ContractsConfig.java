package com.nulink.livingratio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Configuration
@ConfigurationProperties(prefix = "contracts")
public class ContractsConfig {

    private List<ContractInfo> contractList = new ArrayList<>();

    @Data
    public static class ContractInfo {

        private String name;

        public String getName() {
            if (name != null) {
                return name.toLowerCase();
            }
            return null;
        }

        public void setName(String name) {
            if (name != null) {
                this.name = name.toLowerCase();
            }
        }

        private String address;

        private Boolean enabled;

        public String getAddress() {
            if (address != null) {
                return address.toLowerCase();
            }
            return address;
        }

    }

    private Map<String, ContractInfo> mapProps = null;

    public Map<String, ContractInfo> getContractInfoMap() {

        if (ObjectUtils.isEmpty(mapProps)) {

            mapProps = new HashMap<>(contractList.size());

            for (ContractInfo contractInfo : contractList) {
                mapProps.put(contractInfo.getName(), contractInfo);
            }

        }
        return mapProps;
    }

    public ContractInfo getContractInfo(String contractName) {
        Map<String, ContractsConfig.ContractInfo> contractInfoMap = getContractInfoMap();
        return contractInfoMap.get(contractName.toLowerCase());
    }

    public List<String> getContractAddresses() {
        return contractList.stream().map(ContractInfo::getAddress).map(String::toLowerCase).collect(Collectors.toList());
    }


    public List<String> getEnabledContractAddresses() {
        return contractList.stream().filter(ContractInfo::getEnabled).map(ContractInfo::getAddress).map(String::toLowerCase).collect(Collectors.toList());
    }
}
