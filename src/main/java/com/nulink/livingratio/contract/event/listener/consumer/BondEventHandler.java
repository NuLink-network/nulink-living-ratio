package com.nulink.livingratio.contract.event.listener.consumer;

import com.nulink.livingratio.contract.event.listener.filter.events.ContractsEventEnum;
import com.nulink.livingratio.contract.event.listener.filter.events.impl.ContractsEventBuilder;
import com.nulink.livingratio.entity.Bond;
import com.nulink.livingratio.service.BondService;
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
public class BondEventHandler {

    private static BondService bondService;
    private static Web3jUtils web3jUtils;

    public BondEventHandler(BondService bondService, Web3jUtils web3jUtils) {
        BondEventHandler.bondService = bondService;
        BondEventHandler.web3jUtils = web3jUtils;
    }

    public static void descOperatorBonded(Log evLog){
        List<String> topics = evLog.getTopics();
        String data = EthLogsParser.hexToBigInteger(evLog.getData()).toString();
        Bond bond = new Bond();
        String transactionHash = evLog.getTransactionHash();
        Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStamp(transactionHash);
        bond.setTxHash(transactionHash);
        bond.setStakingProvider(EthLogsParser.hexToAddress(topics.get(1)));
        bond.setOperator(EthLogsParser.hexToAddress(topics.get(2)));
        bond.setStartTimestamp(data);
        bond.setCreateTime(eventHappenedTimeStamp);
        bond.setLastUpdateTime(eventHappenedTimeStamp);
        bondService.create(bond);
    }
}
