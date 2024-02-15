package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nulink.livingratio.entity.*;
import com.nulink.livingratio.repository.BondRepository;
import com.nulink.livingratio.repository.StakeRepository;
import com.nulink.livingratio.repository.StakeRewardRepository;
import com.nulink.livingratio.utils.HttpClientUtil;
import com.nulink.livingratio.utils.RedisService;
import com.nulink.livingratio.utils.Web3jUtils;
import com.nulink.livingratio.vo.PorterRequestVO;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@Service
public class StakeRewardService {

    private static final Object countPreviousEpochStakeRewardTaskKey = new Object();
    private static boolean lockCountPreviousEpochStakeRewardTaskFlag = false;

    private static final Object generateCurrentEpochValidStakeRewardTaskKey = new Object();
    private static boolean generateCurrentEpochValidStakeRewardTaskFlag = false;

    private static final Object livingRatioTaskKey = new Object();
    private static boolean livingRatioTaskFlag = false;

    private static String STAKE_EVENT = "stake";

    private static String UN_STAKE_EVENT = "unstake";

    private static String PING = "/ping";

    private static final String DESC_SORT = "desc";
    private static final String ASC_SORT = "asc";

    private final String INCLUDE_URSULA = "/include/ursulas";
    private final String CHECK_URSULA_API = "/check/ursula";

    @Value("${NULink.porter-service-url}")
    private String porterServiceUrl;

    private final StakeRewardRepository stakeRewardRepository;
    private final StakeRepository stakeRepository;
    private final StakeService stakeService;
    private final BondRepository bondRepository;
    private final Web3jUtils web3jUtils;
    private final IncludeUrsulaService includeUrsulaService;

    private final RedisService redisService;

    @Resource
    private PlatformTransactionManager platformTransactionManager;

    public StakeRewardService(StakeRewardRepository stakeRewardRepository,
                              StakeRepository stakeRepository,
                              StakeService stakeService,
                              BondRepository bondRepository,
                              Web3jUtils web3jUtils,
                              IncludeUrsulaService includeUrsulaService, RedisService redisService) {
        this.stakeRewardRepository = stakeRewardRepository;
        this.stakeRepository = stakeRepository;
        this.stakeService = stakeService;
        this.bondRepository = bondRepository;
        this.web3jUtils = web3jUtils;
        this.includeUrsulaService = includeUrsulaService;
        this.redisService = redisService;
    }

    // When the epoch starts, generate the list of stake rewards for the previous epoch
    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    public void generateCurrentEpochValidStakeReward(){
        synchronized (generateCurrentEpochValidStakeRewardTaskKey) {
            if (StakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag) {
                log.warn("The generate Current Epoch Valid StakeReward task is already in progress");
                return;
            }
            StakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = true;
        }

        log.info("The generate Current Epoch Valid StakeReward task is beginning");

        DefaultTransactionDefinition transactionDefinition= new DefaultTransactionDefinition();
        transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = platformTransactionManager.getTransaction(transactionDefinition);

        try{
            String currentEpoch = web3jUtils.getCurrentEpoch();
            String currentEpochStartTime = web3jUtils.getEpochStartTime(currentEpoch);
            if (Integer.valueOf(currentEpoch) < 1){
                StakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
                platformTransactionManager.commit(status);
                return;
            }
            List<StakeReward> stakeRewardList = stakeRewardRepository.findAllByEpoch(currentEpoch);
            if (!stakeRewardList.isEmpty()){
                StakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
                log.info("The Current Epoch Valid StakeReward task has already been executed.");
                platformTransactionManager.commit(status);
                return;
            }
            List<Stake> validStake = stakeService.findValidStakeByEpoch(currentEpoch);
            if (validStake.isEmpty()){
                StakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
                platformTransactionManager.commit(status);
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
            platformTransactionManager.commit(status);
            StakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
            log.info("The generate Current Epoch Valid StakeReward task is finish");
        }catch (Exception e){
            platformTransactionManager.rollback(status);
            log.error("The generate Current Epoch Valid StakeReward task fail:" + e);
            StakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
        }finally {
            StakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
        }
    }

    @Async
    @Scheduled(cron = "0 0 * * * ?")
    //@Scheduled(cron = "0 0/5 * * * ? ")
    public void livingRatio() {
        synchronized (livingRatioTaskKey) {
            if (StakeRewardService.livingRatioTaskFlag) {
                log.warn("The living Ratio task is already in progress");
                return;
            }
            StakeRewardService.livingRatioTaskFlag = true;
        }

        DefaultTransactionDefinition transactionDefinition= new DefaultTransactionDefinition();
        transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = platformTransactionManager.getTransaction(transactionDefinition);

        try{
            String epoch = web3jUtils.getCurrentEpoch();
            if (Integer.parseInt(epoch) < 1){
                StakeRewardService.livingRatioTaskFlag = false;
                platformTransactionManager.commit(status);
                return;
            }
            log.info("living ratio task start ...........................");
            List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpochOrderByCreateTime(epoch);
            List<Bond> latestBonds = bondRepository.findLatest();
            List<Stake> latestStakes = stakeRepository.findLatest();

            Map<String, Bond> latestBoundMap = new HashMap<>();
            Map<String, Stake> latestStakeMap = new HashMap<>();
            latestBonds.forEach(bond -> latestBoundMap.put(bond.getStakingProvider(), bond));
            latestStakes.forEach(stake -> latestStakeMap.put(stake.getUser(), stake));

            List<String> stakingAddress = stakeRewards.stream().map(StakeReward::getStakingProvider).collect(Collectors.toList());
            List<String> nodeAddresses = findNodeAddress(stakingAddress);

            // check node status
            CheckNodeExecutor checkNodeExecutor = new CheckNodeExecutor();
            List<CheckNodeExecutor.ServerStatus> serverStatuses = new ArrayList<>();
            for (int i = 0; i < stakingAddress.size(); i++) {
                String address = nodeAddresses.get(i);
                if(StringUtils.isNotEmpty(address)){
                    serverStatuses.add(new CheckNodeExecutor.ServerStatus(address, stakingAddress.get(i)));
                }
            }
            List<CheckNodeExecutor.ServerStatus> serverStatusesResult = checkNodeExecutor.executePingTasks(serverStatuses);
            Map<String, Boolean> nodeCheckMap = new HashMap<>();
            serverStatusesResult.forEach(serverStatus -> nodeCheckMap.put(serverStatus.getStakingProvider(), serverStatus.isOnline()));
            checkNodeExecutor.shutdown(); // check node finish , executor shutdown

            int connectable = 0;

            for (StakeReward stakeReward : stakeRewards) {
                stakeReward.setPingCount(stakeReward.getPingCount() + 1);
                String stakingProvider = stakeReward.getStakingProvider();

                Bond bond = latestBoundMap.get(stakingProvider);
                Stake stake = latestStakeMap.get(stakingProvider);
                if (null == bond || bond.getOperator().equals("0x0000000000000000000000000000000000000000") || stake.getEvent().equals("unstake")){
                    stakeReward.setUnStake(stakeReward.getUnStake() + 1);
                } else {
                    String nodeAddress  = nodeAddresses.get(stakingAddress.indexOf(stakingProvider));
                    if (StringUtils.isNotEmpty(nodeAddress)){
                        String ipAddress = getIpAddress(nodeAddress);
                        if (StringUtils.isEmpty(stakeReward.getIpAddress())){
                            stakeReward.setIpAddress(ipAddress);
                        } else {
                            if (!stakeReward.getIpAddress().equals(ipAddress)){
                                stakeReward.setIpAddress(ipAddress);
                            }
                        }
                        Boolean b = nodeCheckMap.get(stakeReward.getStakingProvider());
                        if (b != null && b){
                            stakeReward.setConnectable(stakeReward.getConnectable() + 1);
                            connectable ++;
                        } else {
                            stakeReward.setConnectFail(stakeReward.getConnectFail() + 1);
                        }
                    } else {
                        stakeReward.setConnectFail(stakeReward.getConnectFail() + 1);
                    }
                }
                stakeReward.setLivingRatio(new BigDecimal(stakeReward.getConnectable()).divide(new BigDecimal(stakeReward.getPingCount()), 4, RoundingMode.HALF_UP).toString());
            }
            stakeRewardRepository.saveAll(stakeRewards);
            includeUrsulaService.setIncludeUrsula(connectable);
            platformTransactionManager.commit(status);
            log.info("living ratio task finish ...........................");
            StakeRewardService.livingRatioTaskFlag = false;
        }catch (Exception e){
            log.error("The living Ratio task is failed");
            platformTransactionManager.rollback(status);
            StakeRewardService.livingRatioTaskFlag = false;
            throw new RuntimeException(e);
        }finally {
            StakeRewardService.livingRatioTaskFlag = false;
        }

    }

    @Async
    @Scheduled(cron = "0 0/1 * * * ? ")
    public void countPreviousEpochStakeReward(){

        synchronized (countPreviousEpochStakeRewardTaskKey) {
            if (StakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag) {
                log.warn("The count Previous Epoch Stake Reward task is already in progress");
                return;
            }
            StakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag = true;
        }

        log.info("The count Previous Epoch Stake Reward task is beginning");

        DefaultTransactionDefinition transactionDefinition= new DefaultTransactionDefinition();
        transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = platformTransactionManager.getTransaction(transactionDefinition);

        try{
            String previousEpoch = new BigDecimal(web3jUtils.getCurrentEpoch()).subtract(new BigDecimal(1)).toString();
            List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpochOrderByCreateTime(previousEpoch);
            if (stakeRewards.isEmpty()){
                StakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag = false;
                platformTransactionManager.commit(status);
                return;
            }
            if (null == stakeRewards.get(0).getStakingReward()){
                countStakeReward(stakeRewards, previousEpoch);
                stakeRewardRepository.saveAll(stakeRewards);
            } else {
                log.info("The count Previous Epoch Stake Reward task has already been executed.");
            }
            platformTransactionManager.commit(status);
            StakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag = false;
            log.info("The count Previous Epoch Stake Reward task is finish");
        } catch (Exception e){
            log.error("The count Previous Epoch Stake Reward task is fail", e);
            platformTransactionManager.rollback(status);
            StakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag = false;
        } finally {
            StakeRewardService.lockCountPreviousEpochStakeRewardTaskFlag = false;
        }
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
                    String validStakingQuota = new BigDecimal(stakeReward.getValidStakingAmount()).divide(new BigDecimal(totalValidStakingAmount),6, RoundingMode.HALF_UP).toString();
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
        Stake unStake = stakeRepository.findFirstByUserAndEventAndCreateTimeBeforeOrderByCreateTimeDesc(stakingAddress, UN_STAKE_EVENT, new Timestamp(Long.parseLong(epochTime) * 1000));
        List<Stake> stakes;
        if (unStake != null) {
            Timestamp unStakeCreateTime = unStake.getCreateTime();
            stakes = stakeRepository.findAllByUserAndEventAndCreateTimeBetween(stakingAddress, STAKE_EVENT, unStakeCreateTime, new Timestamp(Long.parseLong(epochTime) * 1000));
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
    public List<String> findNodeAddress(List<String> stakingAddress) throws IOException{

        List<String> result = new ArrayList<>();
        int batchSize = 500;
        int totalElements = stakingAddress.size();
        int batches = (int) Math.ceil((double) totalElements / batchSize);

        OkHttpClient client = HttpClientUtil.getUnsafeOkHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        MediaType mediaType = MediaType.parse("application/json");
        String url = porterServiceUrl + INCLUDE_URSULA;
        for (int i = 0; i < batches; i++) {
            int fromIndex = i * batchSize;
            int toIndex = Math.min((i + 1) * batchSize, totalElements);
            List<String> batchList = stakingAddress.subList(fromIndex, toIndex);

            Map<String, List<String>> requestMap = new HashMap<>();
            requestMap.put("include_ursulas", batchList);
            String requestJson = objectMapper.writeValueAsString(requestMap);
            RequestBody requestBody = RequestBody.create(mediaType, requestJson);
            Request request = new Request.Builder().url(url).post(requestBody).build();

            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                PorterRequestVO porterRequestVO = objectMapper.readValue(response.body().string(), PorterRequestVO.class);
                result.addAll(porterRequestVO.getResult().getList());
            } else {
                log.error("Failed to fetch work address for batch: " + i);
            }
            response.close();
        }
        return result;
    }

    public boolean pingNode(String nodeAddress) {
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

    public boolean checkNode(String stakeAddress) throws IOException {
        try{
            OkHttpClient client = HttpClientUtil.getUnsafeOkHttpClient();
            HttpUrl.Builder urlBuilder = HttpUrl.parse(porterServiceUrl + CHECK_URSULA_API).newBuilder();
            urlBuilder.addQueryParameter("staker_address", stakeAddress);
            String url = urlBuilder.build().toString();
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(response.body().string());
                response.body().close();
                return jsonNode.has("result") && jsonNode.get("result").has("data");
            } else {
                log.error("check ursula failed. Response code: " + response.code());
                return false;
            }
        } catch (Exception e){
            log.error("check ursula failed. -" + stakeAddress + "-" + e.getMessage());
            return false;
        }
    }

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
            List<String> nodeAddress = null;
            try {
                nodeAddress = findNodeAddress(Collections.singletonList(stake.getUser()));
            } catch (IOException e) {
                log.error("fetch node url in porter failed:" + e.getMessage());
                throw new RuntimeException(e);
            }
            String nodeUrl = nodeAddress.get(0);
            if (StringUtils.isNotEmpty(nodeUrl)){
                String ipAddress = getIpAddress(nodeUrl);
                stakeReward.setIpAddress(ipAddress);
                try {
                    stakeReward.setOnline(checkNode(stakingProvider));
                } catch (IOException e) {
                    log.error("check node status failed:" + e.getMessage());
                    throw new RuntimeException(e);
                }
            } else {
                /*StakeReward s = stakeRewardRepository.findFirstByStakingProviderAndIpAddressIsNotNullOrderByCreateTimeDesc(stakingProvider);
                if (null != s){
                    stakeReward.setIpAddress(s.getIpAddress());
                }*/
                stakeReward.setOnline(false);
            }
        } else {
            stakeReward.setOnline(false);
        }
        return stakeReward;
    }

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

    public Page<StakeReward> findPage(String epoch, int pageSize, int pageNum, String orderBy, String sorted){
        String stakeRewardPageKey = "stakeRewardPage_epoch_" + epoch;
        List<StakeReward> stakeRewards = new ArrayList<>();
        try {
            Object redisValue = redisService.get(stakeRewardPageKey);
            if (null != redisValue) {
                String v = redisValue.toString();
                stakeRewards = JSONObject.parseArray(v, StakeReward.class);
            }
        }catch (Exception e){
            log.error("StakeReward find page redis read error：{}", e.getMessage());
        }
        if (stakeRewards.isEmpty()){
            stakeRewards = stakeRewardRepository.findAllByEpochOrderByCreateTime(epoch);
            if (!stakeRewards.isEmpty()){
                try {
                    String pvoStr = JSON.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
                    redisService.set(stakeRewardPageKey, pvoStr, 300, TimeUnit.SECONDS);
                }catch (Exception e){
                    log.error("StakeReward find page redis write error：{}", e.getMessage());
                }
            }
        }
        return pageHelper(pageSize, pageNum, orderBy, sorted, stakeRewards);
    }

    public Page<StakeReward> findCurrentEpochPage(int pageSize, int pageNum, String orderBy, String sorted){
        String epoch = web3jUtils.getCurrentEpoch();
        String currentEpochStakeReward = "currentEpochStakeReward_epoch_" + epoch;
        List<StakeReward> stakeRewards = new ArrayList<>();
        try {
            Object redisValue = redisService.get(currentEpochStakeReward);
            if (null != redisValue) {
                String v = redisValue.toString();
                stakeRewards = JSONObject.parseArray(v, StakeReward.class);
            }
        }catch (Exception e){
            log.error("stakeReward findCurrentEpochPage redis read error：{}", e.getMessage());
        }

        if (stakeRewards.isEmpty()){
            stakeRewards = stakeRewardRepository.findAllByEpochOrderByCreateTime(epoch);
            countStakeReward(stakeRewards, epoch);
            if (!stakeRewards.isEmpty()){
                try {
                    String pvoStr = JSON.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
                    redisService.set(currentEpochStakeReward, pvoStr, 30, TimeUnit.SECONDS);
                }catch (Exception e){
                    log.error("stakeReward findCurrentEpochPage redis write error：{}", e.getMessage());
                }
            }
        }

        return pageHelper(pageSize, pageNum, orderBy, sorted, stakeRewards);
    }

    @NotNull
    private Page<StakeReward> pageHelper(int pageSize, int pageNum, String orderBy, String sorted, List<StakeReward> stakeRewards) {
        Comparator<StakeReward> comparator = null;
        if ("livingRatio".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getLivingRatio()));
        } else if ("stakingAmount".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getStakingAmount()));
        } else if ("stakingReward".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getStakingReward()));
        } else if ("validStakingAmount".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getValidStakingAmount()));
        } else if ("validStakingQuota".equalsIgnoreCase(orderBy)) {
            comparator = Comparator.comparing(sr -> new BigDecimal(sr.getValidStakingQuota()));
        }

        if (comparator != null) {
            if (DESC_SORT.equalsIgnoreCase(sorted)) {
                comparator = comparator.reversed();
            }
            Collections.sort(stakeRewards, comparator);
        }

        int endIndex = pageNum * pageSize;
        int size = stakeRewards.size();
        endIndex = Math.min(endIndex, size);
        List<StakeReward> subList = stakeRewards.subList((pageNum - 1) * pageSize, endIndex);
        return new PageImpl<>(subList, PageRequest.of(pageNum - 1, pageSize), size);
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

    public boolean checkAllOnlineWithinOneEpoch(String stakingProvider){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        int i = stakeRewardRepository.countStakingProviderAllOnlineEpoch(stakingProvider, currentEpoch);
        return i > 0;
    }

}
