package com.nulink.livingratio.contract.event.listener.consumer;

import com.nulink.livingratio.contract.event.listener.filter.events.ContractsEventEnum;
import com.nulink.livingratio.contract.event.listener.filter.events.impl.ContractsEventBuilder;
import com.nulink.livingratio.entity.Bond;
import com.nulink.livingratio.entity.ClaimReward;
import com.nulink.livingratio.entity.FaucetNLK;
import com.nulink.livingratio.service.FaucetNLKService;
import com.nulink.livingratio.utils.EthLogsParser;
import com.nulink.livingratio.utils.Web3jUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.List;

@Slf4j
@Component
public class FaucetEventHandler {

    private static Web3jUtils web3jUtils;

    private static FaucetNLKService faucetNLKService;

    public FaucetEventHandler(Web3jUtils web3jUtils, FaucetNLKService faucetNLKService) {
        FaucetEventHandler.web3jUtils = web3jUtils;
        FaucetEventHandler.faucetNLKService = faucetNLKService;
    }

    public static void descFaucetNLKReward(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.TEST_NLK);
        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();
        if (!CollectionUtils.isEmpty(args)) {
            FaucetNLK faucetNLK = new FaucetNLK();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStamp(transactionHash);
            faucetNLK.setTxHash(transactionHash);
            faucetNLK.setUser(args.get(0).getValue().toString());
            faucetNLK.setCountryCode(args.get(1).getValue().toString());
            faucetNLK.setIpAddress(args.get(2).getValue().toString());
            faucetNLK.setCreateTime(eventHappenedTimeStamp);
            faucetNLK.setLastUpdateTime(eventHappenedTimeStamp);
            faucetNLKService.create(faucetNLK);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }
}
