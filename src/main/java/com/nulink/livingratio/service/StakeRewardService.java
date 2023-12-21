package com.nulink.livingratio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nulink.livingratio.entity.*;
import com.nulink.livingratio.repository.BondRepository;
import com.nulink.livingratio.repository.SetLivingRatioRepository;
import com.nulink.livingratio.repository.StakeRepository;
import com.nulink.livingratio.repository.StakeRewardRepository;
import com.nulink.livingratio.utils.HttpClientUtil;
import com.nulink.livingratio.utils.Web3jUtils;
import com.nulink.livingratio.vo.PorterRequestVO;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@Service
public class StakeRewardService {

    private static final Object countPreviousEpochStakeRewardTaskKey = new Object();
    private static boolean lockCountPreviousEpochStakeRewardTaskFlag = false;

    private static String STAKE_EVENT = "stake";

    private static String UN_STAKE_EVENT = "unstake";

    private final String PING = "/ping";

    private final String INCLUDE_URSULA = "/include/ursulas";
    private final String CHECK_URSULA_API = "/check/ursula";

    @Value("${NULink.porter-service-url}")
    private String porterServiceUrl;

    private final StakeRewardRepository stakeRewardRepository;
    private final StakeRepository stakeRepository;
    private final StakeService stakeService;
    private final BondRepository bondRepository;
    private final Web3jUtils web3jUtils;
    private final SetLivingRatioRepository setLivingRatioRepository;

    public StakeRewardService(StakeRewardRepository stakeRewardRepository,
                              StakeRepository stakeRepository,
                              StakeService stakeService,
                              BondRepository bondRepository,
                              Web3jUtils web3jUtils,
                              SetLivingRatioRepository setLivingRatioRepository) {
        this.stakeRewardRepository = stakeRewardRepository;
        this.stakeRepository = stakeRepository;
        this.stakeService = stakeService;
        this.bondRepository = bondRepository;
        this.web3jUtils = web3jUtils;
        this.setLivingRatioRepository = setLivingRatioRepository;
    }

    // When the epoch starts, generate the list of stake rewards for the previous epoch
    @Async
    @Scheduled(cron = "0 0/2 * * * ? ")
    @Transactional
    public void generateCurrentEpochValidStakeReward(){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        String currentEpochStartTime = web3jUtils.getEpochStartTime(currentEpoch);
        if (Integer.valueOf(currentEpoch) < 1){
            return;
        }
        List<StakeReward> stakeRewardList = stakeRewardRepository.findAllByEpoch(currentEpoch);
        if (!stakeRewardList.isEmpty()){
            return;
        }
        List<Stake> validStake = stakeService.findValidStakeByEpoch(currentEpoch);
        if (validStake.isEmpty()){
            return;
        }
        validStake = validStake.stream().filter(stake -> !stake.getAmount().equals("0")).collect(Collectors.toList());
        List<Bond> bounds = bondRepository.findLatestBond();
        HashMap<String, String> bondMap  = new HashMap<>();
        bounds.forEach(bond -> bondMap.put(bond.getStakingProvider(), bond.getOperator()));
        List<String> stakingAddress = validStake.stream().map(Stake::getUser).collect(Collectors.toList());

        List<String> nodeAddresses = findNodeAddress(stakingAddress);

        List<StakeReward> stakeRewards = new ArrayList<>();

        validStake.forEach(stake -> {
            StakeReward stakeReward = new StakeReward();
            stakeReward.setEpoch(currentEpoch);
            String stakeUser = stake.getUser();
            if (bondMap.get(stakeUser) != null) {
                stakeReward.setOperator(bondMap.get(stakeUser));
                String url = nodeAddresses.get(stakingAddress.indexOf(stakeUser));
                if (StringUtils.isNotEmpty(url)){
                    stakeReward.setIpAddress(getIpAddress(url));
                }
            }
            stakeReward.setStakingProvider(stakeUser);
            stakeReward.setStakingAmount(getStakingAmount(stakeUser, currentEpochStartTime));
            stakeReward.setValidStakingAmount("0");
            stakeReward.setLivingRatio("0");
            stakeRewards.add(stakeReward);
        });
        stakeRewardRepository.saveAll(stakeRewards);
    }

    @Async
    //@Scheduled(cron = "0 30 * * * ? ")
    @Scheduled(cron = "0 0/5 * * * ? ")
    @Transactional
    public void livingRatio(){
        String epoch = web3jUtils.getCurrentEpoch();
        if (Integer.valueOf(epoch) < 1){
            return;
        }
        List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpochOrderByCreateTime(epoch);
        List<Bond> latestBonds = bondRepository.findLatest();
        List<Stake> latestStakes = stakeRepository.findLatest();

        Map<String, Bond> latestBoundMap = new HashMap<>();
        Map<String, Stake> latestStakeMap = new HashMap<>();
        latestBonds.forEach(bond -> latestBoundMap.put(bond.getStakingProvider(), bond));
        latestStakes.forEach(stake -> latestStakeMap.put(stake.getUser(), stake));

        List<String> stakingAddress = stakeRewards.stream().map(StakeReward::getStakingProvider).collect(Collectors.toList());

        List<String> nodeAddresses = findNodeAddress(stakingAddress);

        for (StakeReward stakeReward : stakeRewards) {
            stakeReward.setPingCount(stakeReward.getPingCount() + 1);
            String stakingProvider = stakeReward.getStakingProvider();
            String nodeAddress  = nodeAddresses.get(stakingAddress.indexOf(stakeReward.getStakingProvider()));
            if (StringUtils.isNotEmpty(nodeAddress)){
                String ipAddress = getIpAddress(nodeAddress);
                if (stakeReward.getIpAddress().isEmpty()){
                    stakeReward.setIpAddress(ipAddress);
                } else {
                    if (!stakeReward.getIpAddress().equals(ipAddress)){
                        stakeReward.setIpAddress(ipAddress);
                    }
                }
                Bond bond = latestBoundMap.get(stakingProvider);
                Stake stake = latestStakeMap.get(stakingProvider);
                if (bond.getOperator().isEmpty() && stake.getEvent().equals("unstake")){
                    stakeReward.setUnStake(stakeReward.getUnStake() + 1);
                } else {
                    boolean connectable = pingNode(nodeAddress);
                    if (connectable){
                        stakeReward.setConnectable(stakeReward.getConnectable() + 1);
                    } else {
                        stakeReward.setConnectFail(stakeReward.getConnectFail() + 1);
                    }
                }
            } else {
                stakeReward.setUnStake(stakeReward.getUnStake() + 1);
            }
            stakeReward.setLivingRatio(new BigDecimal(stakeReward.getConnectable()).divide(new BigDecimal(stakeReward.getPingCount()), 4, RoundingMode.HALF_UP).toString());
        }
        stakeRewardRepository.saveAll(stakeRewards);
    }

    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    @Transactional
    public void countPreviousEpochStakeReward(){

        synchronized (countPreviousEpochStakeRewardTaskKey) {
            if (StakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag) {
                log.warn("The set living ratio task is already in progress");
                return;
            }
            StakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag = true;
        }

        String previousEpoch = new BigDecimal(web3jUtils.getCurrentEpoch()).subtract(new BigDecimal(1)).toString();
        List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpochOrderByCreateTime(previousEpoch);
        if (stakeRewards.isEmpty()){
            return;
        }
        if (null == stakeRewards.get(0).getStakingReward()){
            countStakeReward(stakeRewards, previousEpoch);
            stakeRewardRepository.saveAll(stakeRewards);
            SetLivingRatio setLivingRatio = new SetLivingRatio();
            setLivingRatio.setSetLivingRatio(false);
            setLivingRatio.setEpoch(previousEpoch);
            setLivingRatioRepository.save(setLivingRatio);
        }
        StakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag = false;
    }

    public void countStakeReward(List<StakeReward> stakeRewards, String epoch){
        if (!stakeRewards.isEmpty()){
            for (StakeReward stakeReward : stakeRewards) {
                if (StringUtils.isNotEmpty(stakeReward.getLivingRatio())){
                    stakeReward.setValidStakingAmount(new BigDecimal(stakeReward.getStakingAmount()).multiply(new BigDecimal(stakeReward.getLivingRatio())).setScale(0, RoundingMode.HALF_UP).toString());
                }
            }
            String totalValidStakingAmount = sum(stakeRewards.stream().map(StakeReward::getValidStakingAmount).collect(Collectors.toList()));
            String currentEpochReward = web3jUtils.getEpochReward(epoch);
            for (StakeReward stakeReward : stakeRewards) {
                if (new BigDecimal(totalValidStakingAmount).compareTo(BigDecimal.ZERO) > 0){
                    String validStakingQuota = new BigDecimal(stakeReward.getValidStakingAmount()).divide(new BigDecimal(totalValidStakingAmount),4, RoundingMode.HALF_UP).toString();
                    stakeReward.setValidStakingQuota(validStakingQuota);
                    stakeReward.setStakingReward(new BigDecimal(validStakingQuota).multiply(new BigDecimal(currentEpochReward)).setScale(0, RoundingMode.HALF_UP).toString());
                } else {
                    stakeReward.setValidStakingQuota("0");
                    stakeReward.setStakingReward("0");
                }
            }
        }
    }

    private String sum(List<String> amount){
        BigDecimal sum = BigDecimal.ZERO;
        for (String s : amount) {
            sum = sum.add(new BigDecimal(s));
        }
        return sum.toString();
    }

    private String getStakingAmount(String stakingAddress, String epochTime){
        Stake unStake = stakeRepository.findFirstByUserAndEventAndCreateTimeBeforeOrderByCreateTimeDesc(stakingAddress, UN_STAKE_EVENT, new Date(Long.parseLong(epochTime) * 1000));
        List<Stake> stakes;
        if (unStake != null) {
            Date unStakeCreateTime = unStake.getCreateTime();
            stakes = stakeRepository.findAllByUserAndEventAndCreateTimeBetween(stakingAddress, STAKE_EVENT, unStakeCreateTime, new Date(Long.parseLong(epochTime) * 1000));
        } else {
            stakes = stakeRepository.findAllByUser(stakingAddress);
        }
        if (stakes.isEmpty()){
            return BigDecimal.ZERO.toString();
        }
        List<BigDecimal> amounts = stakes.stream().map(stake -> new BigDecimal(stake.getAmount())).collect(Collectors.toList());
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).toString();
    }

    private String getIpAddress(String url){
        if (null == url){
            return null;
        }
        return url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf(":"));
    }

    /**
     * findNodeAddress
     * @param stakingAddress
     * @return
     */
    public List<String> findNodeAddress(List<String> stakingAddress){
        Map<String, List<String>> requestMap = new HashMap<>();
        requestMap.put("include_ursulas", stakingAddress);
        try {
            MediaType mediaType = MediaType.parse("application/json");
            ObjectMapper objectMapper = new ObjectMapper();
            String requestJson = objectMapper.writeValueAsString(requestMap);
            RequestBody requestBody =  RequestBody.create(mediaType, requestJson);
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(porterServiceUrl + INCLUDE_URSULA).post(requestBody).build();

            Response response = client.newCall(request).execute();
            if (response.isSuccessful()){
                PorterRequestVO porterRequestVO = objectMapper.readValue(response.body().string(), PorterRequestVO.class);
                return porterRequestVO.getResult().getList();
            } else {
                response.close();
                log.error("Failed to fetch work address");
                return null;
            }
        }  catch (IOException e) {
            log.error("Failed to retrieve worker address", e);
            throw new RuntimeException(e);
        }
    }

    private boolean pingNode(String nodeAddress) {
        OkHttpClient client = HttpClientUtil.getUnsafeOkHttpClient();
        Request request = new Request.Builder()
                .url(nodeAddress + PING)
                .build();
        Call call = client.newCall(request);

        Response response;
        try {
            response = call.execute();
        } catch (IOException e) {
            log.error("Node connect failure:" + nodeAddress);
            throw new RuntimeException(e.getMessage());
        }
        if (response.isSuccessful()) {
            response.close();
            return true;
        } else {
            log.error("Request failed. Response code: " + response.code());
            response.close();
            return false;
        }
    }

    public boolean checkNode(String stakeAddress){
        OkHttpClient client = new OkHttpClient();
        HttpUrl.Builder urlBuilder = HttpUrl.parse(porterServiceUrl + CHECK_URSULA_API).newBuilder();
        urlBuilder.addQueryParameter("staker_address", stakeAddress);
        String url = urlBuilder.build().toString();

        Request request = new Request.Builder() .url(url) .build();

        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(response.body().string());
                return jsonNode.has("result") && jsonNode.get("result").has("data");
            } else {
                log.error("check ursula failed. Response code: " + response.code());
                throw new RuntimeException("check ursula failed. Response code: " + response.code());
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Transactional
    public StakeReward nodeInfo(String stakingProvider){
        Stake stake = stakeRepository.findFirstByUserAndEventOrderByCreateTimeDesc(stakingProvider, STAKE_EVENT);
        if (null == stake){
            return null;
        }
        StakeReward stakeReward = new StakeReward();
        stakeReward.setStakingProvider(stake.getUser());
        Bond bond = bondRepository.findLastOneBondByStakingProvider(stakingProvider);
        if (null != bond){
            stakeReward.setOperator(bond.getOperator());
            List<String> nodeAddress = findNodeAddress(Collections.singletonList(stake.getUser()));
            String nodeUrl = nodeAddress.get(0);
            if (StringUtils.isNotEmpty(nodeUrl)){
                String ipAddress = getIpAddress(nodeUrl);
                stakeReward.setIpAddress(ipAddress);
                stakeReward.setOnline(pingNode(nodeUrl));
            } else {
                stakeReward.setOnline(false);
            }
        } else {
            stakeReward.setOnline(false);
        }
        return stakeReward;
    }

    @Transactional
    public StakeReward findByEpochAndStakingProvider(String stakingProvider, String epoch){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        if (currentEpoch.equals(epoch)){
            List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpoch(epoch);
            countStakeReward(stakeRewards, epoch);
            for (StakeReward stakeReward : stakeRewards) {
                if (stakeReward.getStakingProvider().equals(stakingProvider)){
                    return stakeReward;
                }
            }
        } else {
            return stakeRewardRepository.findByEpochAndStakingProvider(epoch, stakingProvider);
        }
        return null;
    }

    public Page<StakeReward> findPage(String epoch, int pageSize, int pageNum){
        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Order.asc("createTime")));
        Specification<StakeReward> specification = (root, query, criteriaBuilder) -> {
            if (StringUtils.isNotEmpty(epoch)) {
                return criteriaBuilder.equal(root.get("epoch"), epoch);
            }
            return null;
        };
        return stakeRewardRepository.findAll(specification, pageable);
    }

    public Page<StakeReward> findCurrentEpochPage(int pageSize, int pageNum){
        String epoch = web3jUtils.getCurrentEpoch();
        Page<StakeReward> stakeRewardPage = findPage(epoch, pageSize, pageNum);
        countStakeReward(stakeRewardPage.getContent(), epoch);
        return stakeRewardPage;
    }

    public List<StakeReward> list(String epoch){
        return stakeRewardRepository.findAllByEpochOrderByCreateTime(epoch);
    }

    public String userTotalStakingReward(String address){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        List<StakeReward> stakeRewards = stakeRewardRepository.findAllByStakingProviderAndEpochNot(address, currentEpoch);
        BigDecimal total = BigDecimal.ZERO;
        for (StakeReward stakeReward : stakeRewards) {
            String stakingReward = stakeReward.getStakingReward();
            if (StringUtils.isNotEmpty(stakingReward)){
                total = total.add(new BigDecimal(stakingReward));
            }
        }
        List<StakeReward> currentEpochStakeRewards = stakeRewardRepository.findAllByEpoch(currentEpoch);
        countStakeReward(currentEpochStakeRewards, currentEpoch);
        for (StakeReward currentEpochStakeReward : currentEpochStakeRewards) {
            if (address.equalsIgnoreCase(currentEpochStakeReward.getStakingProvider())){
                total = total.add(new BigDecimal(currentEpochStakeReward.getStakingReward()));
            }
        }
        return total.toString();
    }
}
