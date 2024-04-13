package com.nulink.livingratio.config;

import org.springframework.stereotype.Component;

@Component
public class RpcConfig {

    private String rpcUrl;

    public String getRpcUrl() {
        return rpcUrl;
    }

    public void setRpcUrl(String rpcUrl) {
        this.rpcUrl = rpcUrl;
    }
}
