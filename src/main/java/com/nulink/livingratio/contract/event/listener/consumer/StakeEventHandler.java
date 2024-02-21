package com.nulink.livingratio.contract.event.listener.consumer;

import com.nulink.livingratio.contract.event.listener.filter.events.ContractsEventEnum;
import com.nulink.livingratio.contract.event.listener.filter.events.impl.ContractsEventBuilder;
import com.nulink.livingratio.entity.Claim;
import com.nulink.livingratio.entity.ClaimReward;
import com.nulink.livingratio.entity.Stake;
import com.nulink.livingratio.service.ClaimRewardService;
import com.nulink.livingratio.service.ClaimService;
import com.nulink.livingratio.service.StakeService;
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
public class StakeEventHandler {

    private static String STAKE_EVENT = "stake";

    private static String UN_STAKE_EVENT = "unstake";

    private static StakeService stakeService;
    private static Web3jUtils web3jUtils;

    private static ClaimService claimService;

    private static ClaimRewardService claimRewardService;
    public StakeEventHandler(StakeService stakeService, Web3jUtils web3jUtils, ClaimService claimService, ClaimRewardService claimRewardService) {
        StakeEventHandler.stakeService = stakeService;
        StakeEventHandler.web3jUtils = web3jUtils;
        StakeEventHandler.claimService = claimService;
        StakeEventHandler.claimRewardService = claimRewardService;
    }

    public static void descStake(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.STAKE);

        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();

        if (!CollectionUtils.isEmpty(args)) {
            Stake stake = new Stake();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            stake.setTxHash(transactionHash);
            stake.setEvent(STAKE_EVENT);
            stake.setUser(args.get(0).getValue().toString());
            stake.setAmount(args.get(1).getValue().toString());
            stake.setTime(args.get(2).getValue().toString());
            stake.setEpoch(args.get(3).getValue().toString());
            stake.setCreateTime(eventHappenedTimeStamp);
            stake.setLastUpdateTime(eventHappenedTimeStamp);
            stakeService.create(stake);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }

    public static void descUnStakeAll(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.UN_STAKE_ALL);
        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();
        if (!CollectionUtils.isEmpty(args)) {
            Stake stake = new Stake();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            stake.setTxHash(transactionHash);
            stake.setEvent(UN_STAKE_EVENT);
            stake.setUser(args.get(0).getValue().toString());
            stake.setAmount(args.get(1).getValue().toString());
            stake.setTime(args.get(2).getValue().toString());
            stake.setEpoch(args.get(3).getValue().toString());
            stake.setCreateTime(eventHappenedTimeStamp);
            stake.setLastUpdateTime(eventHappenedTimeStamp);
            stakeService.create(stake);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }

    public static void descClaim(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.CLAIM);
        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();
        if (!CollectionUtils.isEmpty(args)) {
            Claim claim = new Claim();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            claim.setTxHash(transactionHash);
            claim.setUser(args.get(0).getValue().toString());
            claim.setAmount(args.get(1).getValue().toString());
            claim.setTime(args.get(2).getValue().toString());
            claim.setEpoch(args.get(3).getValue().toString());
            claim.setCreateTime(eventHappenedTimeStamp);
            claim.setLastUpdateTime(eventHappenedTimeStamp);
            claimService.create(claim);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }

    public static void descClaimReward(Log evLog){
        Event descEvent = new ContractsEventBuilder().build(ContractsEventEnum.CLAIM);
        List<Type> args = FunctionReturnDecoder.decode(evLog.getData(), descEvent.getParameters());
        List<String> topics = evLog.getTopics();
        if (!CollectionUtils.isEmpty(args)) {
            ClaimReward claimReward = new ClaimReward();
            String transactionHash = evLog.getTransactionHash();
            Timestamp eventHappenedTimeStamp = web3jUtils.getEventHappenedTimeStampByBlockHash(evLog.getBlockHash());
            claimReward.setTxHash(transactionHash);
            claimReward.setUser(args.get(0).getValue().toString());
            claimReward.setRewardAmount(args.get(1).getValue().toString());
            claimReward.setTime(args.get(2).getValue().toString());
            claimReward.setLastEpoch(args.get(3).getValue().toString());
            claimReward.setCreateTime(eventHappenedTimeStamp);
            claimReward.setLastUpdateTime(eventHappenedTimeStamp);
            claimRewardService.create(claimReward);
        } else if (!CollectionUtils.isEmpty(topics)) {
            String from = EthLogsParser.hexToAddress(topics.get(1));
            String to = EthLogsParser.hexToAddress(topics.get(2));
            BigInteger tokenId = EthLogsParser.hexToBigInteger(topics.get(3));
            log.info("descOperatorBonded from = {}\n to = {} \n tokenId = {}", from, to, tokenId);
        }
    }
}
