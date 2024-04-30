package com.nulink.livingratio.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
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
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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

    private final ValidStakingAmountService validStakingAmountService;

    private final ContractOffsetService contractOffsetService;
    @Resource
    private PlatformTransactionManager platformTransactionManager;
    @Autowired
    private StakeRewardOverviewService stakingRewardOverviewService;

    private RedisTemplate<String, String> redisTemplate;

    public StakeRewardService(StakeRewardRepository stakeRewardRepository,
                              StakeRepository stakeRepository,
                              StakeService stakeService,
                              BondRepository bondRepository,
                              Web3jUtils web3jUtils,
                              IncludeUrsulaService includeUrsulaService, RedisService redisService,
                              ValidStakingAmountService validStakingAmountService,
                              ContractOffsetService contractOffsetService, RedisTemplate<String, String> redisTemplate) {
        this.stakeRewardRepository = stakeRewardRepository;
        this.stakeRepository = stakeRepository;
        this.stakeService = stakeService;
        this.bondRepository = bondRepository;
        this.web3jUtils = web3jUtils;
        this.includeUrsulaService = includeUrsulaService;
        this.redisService = redisService;
        this.validStakingAmountService = validStakingAmountService;
        this.contractOffsetService = contractOffsetService;
        this.redisTemplate = redisTemplate;
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
            if (!checkScanBlockNumber()){
                StakeRewardService.generateCurrentEpochValidStakeRewardTaskFlag = false;
                log.info("Waiting for scanning block");
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

            Map<String, String> validStakingAmount = validStakingAmountService.findValidStakingAmount(Integer.parseInt(currentEpoch) - 1);

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
                stakeReward.setStakingAmount(validStakingAmount.get(stakeUser) == null?"0":validStakingAmount.get(stakeUser));
                stakeReward.setValidStakingAmount("0");
                stakeReward.setLivingRatio("0");
                stakeRewards.add(stakeReward);
            });
            stakeRewards.removeIf(stakeReward -> stakeReward.getStakingAmount().equals("0"));
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
            List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpoch(epoch);
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
            StakeRewardOverview stakeRewardOverview = stakingRewardOverviewService.getStakeRewardOverview(stakeRewards, epoch);
            stakingRewardOverviewService.saveByEpoch(stakeRewardOverview);
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
            List<StakeReward> stakeRewards = stakeRewardRepository.findAllByEpoch(previousEpoch);
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

    /*public Page<StakeReward> findPage(String epoch, int pageSize, int pageNum, String orderBy, String sorted) {
        StringBuilder stakeRewardPageKey = new StringBuilder("stakeRewardPage:epoch:" + epoch);
        StringBuilder stakeRewardPageCountKey = new StringBuilder("stakeRewardPageCount:epoch:" + epoch);
        if (ObjectUtils.isNotEmpty(pageSize)) {
            stakeRewardPageKey.append(":pageSize:").append(pageSize);
            stakeRewardPageCountKey.append(":pageSize:").append(pageSize);
        }
        if (ObjectUtils.isNotEmpty(pageNum)) {
            stakeRewardPageKey.append(":pageNum:").append(pageNum);
            stakeRewardPageCountKey.append(":pageNum:").append(pageNum);
        }
        if (StringUtils.isNotBlank(orderBy)) {
            stakeRewardPageKey.append(":orderBy:").append(orderBy);
            stakeRewardPageCountKey.append(":orderBy:").append(orderBy);
        }
        if (StringUtils.isNotBlank(sorted)) {
            stakeRewardPageKey.append(":sorted:").append(sorted);
            stakeRewardPageCountKey.append(":sorted:").append(sorted);
        }
        List<StakeReward> stakeRewards;
        try {
            Object listValue = redisService.get(stakeRewardPageKey.toString());
            Object countValue = redisService.get(stakeRewardPageCountKey.toString());
            if (null != listValue && null != countValue) {
                String v = listValue.toString();
                stakeRewards = JSONObject.parseArray(v, StakeReward.class);
                if (!stakeRewards.isEmpty())
                    return new PageImpl<>(stakeRewards, PageRequest.of(pageNum - 1, pageSize), Long.parseLong(countValue.toString()));
            }
        } catch (Exception e) {
            log.error("StakeReward find page redis read error：{}", e.getMessage());
        }
        Sort sort = null;
        if ("livingRatio".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("livingRatio");
        } else if ("stakingAmount".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("stakingAmount");
        } else if ("stakingReward".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("stakingReward");
        } else if ("validStakingAmount".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("validStakingAmount");
        } else if ("validStakingQuota".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("validStakingQuota");
        }
        if (DESC_SORT.equalsIgnoreCase(sorted)) {
            if (sort != null) {
                sort = sort.descending();
            }
        } else {
            if (sort != null) {
                sort = sort.ascending();
            }
        }
        if (null == sort) {
            sort = Sort.by("createTime");
        }
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);

        Specification<StakeReward> specification = (root, query, criteriaBuilder) -> {
            if (StringUtils.isNotEmpty(epoch)) {
                return criteriaBuilder.equal(root.get("epoch"), epoch);
            }
            return null;
        };
        Page<StakeReward> page = stakeRewardRepository.findAll(specification, pageable);
        List<StakeReward> stakeRewardList = page.getContent();
        if (!stakeRewardList.isEmpty()) {
            try {
                String pvoStr = JSON.toJSONString(stakeRewardList, SerializerFeature.WriteNullStringAsEmpty);
                redisService.set(stakeRewardPageKey.toString(), pvoStr, 10, TimeUnit.MINUTES);
                redisService.set(stakeRewardPageCountKey.toString(), String.valueOf(page.getTotalElements()), 10, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("StakeReward find page redis write error：{}", e.getMessage());
            }
        }
        return page;
    }*/

    /*public Page<StakeReward> findCurrentEpochPage(int pageSize, int pageNum, String orderBy, String sorted){
        String epoch = web3jUtils.getCurrentEpoch();
        StringBuilder currentEpochSB = new StringBuilder("currentEpochStakeReward:epoch:" + epoch);
        StringBuilder currentEpochCountSB = new StringBuilder("currentEpochStakeRewardCount:epoch:" + epoch);
        if (ObjectUtils.isNotEmpty(pageSize)){
            currentEpochSB.append(":pageSize:").append(pageSize);
            currentEpochCountSB.append(":pageSize:").append(pageSize);
        }
        if (ObjectUtils.isNotEmpty(pageNum)){
            currentEpochSB.append(":pageNum:").append(pageNum);
            currentEpochCountSB.append(":pageNum:").append(pageNum);
        }
        if (StringUtils.isNotBlank(orderBy)){
            currentEpochSB.append(":orderBy:").append(orderBy);
            currentEpochCountSB.append(":orderBy:").append(orderBy);
        }
        if (StringUtils.isNotBlank(sorted)){
            currentEpochSB.append(":sorted:").append(sorted);
            currentEpochCountSB.append(":sorted:").append(sorted);
        }
        List<StakeReward> stakeRewards;
        try {
            Object listValue = redisService.get(currentEpochSB.toString());
            Object countValue = redisService.get(currentEpochCountSB.toString());
            if (null != listValue && null != countValue) {
                String v = listValue.toString();
                long size = Long.parseLong(countValue.toString());
                stakeRewards = JSONObject.parseArray(v, StakeReward.class);
                if (!stakeRewards.isEmpty()){
                    return new PageImpl<>(stakeRewards, PageRequest.of(pageNum - 1, pageSize), size);
                }
            }
        }catch (Exception e){
            log.error("stakeReward findCurrentEpochPage redis read error：{}", e.getMessage());
        }

        List<StakeReward> all = findAllByEpoch(epoch);
        countStakeReward(all, epoch);
        stakeRewards = pageHelper(pageSize, pageNum, orderBy, sorted, all);
        if (!stakeRewards.isEmpty()){
            try {
                String pvoStr = JSON.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
                redisService.set(currentEpochSB.toString(), pvoStr, 180, TimeUnit.SECONDS);
                redisService.set(currentEpochCountSB.toString(), String.valueOf(all.size()), 180, TimeUnit.SECONDS);
            }catch (Exception e){
                log.error("stakeReward findCurrentEpochPage redis write error：{}", e.getMessage());
            }
        }
        return new PageImpl<>(stakeRewards, PageRequest.of(pageNum - 1, pageSize), all.size());
    }*/

    /*public void deleteAllKeys() {
        Set<String> keys = redisTemplate.keys("*");

        for (String key : keys) {
            redisTemplate.delete(key);
        }
    }*/

    public Page<StakeReward> findPage(String epoch, int pageSize, int pageNum, String orderBy, String sorted) {
        String currentEpoch = web3jUtils.getCurrentEpoch();
        if (epoch.equals(currentEpoch) && ("stakingReward".equalsIgnoreCase(orderBy) || "validStakingAmount".equalsIgnoreCase(orderBy) || "validStakingQuota".equalsIgnoreCase(orderBy))){
            return findCurrentEpochPageOrderHelper(pageSize, pageNum, orderBy, sorted);
        }
        // 构造Key的逻辑封装
        String stakeRewardPageKey = buildKey("stakeRewardPage", epoch, pageSize, pageNum, orderBy, sorted);
        String stakeRewardPageCountKey = buildKey("stakeRewardPageCount", epoch, pageSize, pageNum, orderBy, sorted);

        List<StakeReward> stakeRewards = new ArrayList<>();
        try {
            Object listValue = redisService.get(stakeRewardPageKey);
            Object countValue = redisService.get(stakeRewardPageCountKey);
            if (listValue != null && countValue != null) {
                String v = listValue.toString();
                stakeRewards = JSONObject.parseArray(v, StakeReward.class);
                if (!stakeRewards.isEmpty()){
                    return new PageImpl<>(stakeRewards, PageRequest.of(pageNum - 1, pageSize), Long.parseLong(countValue.toString()));
                }
            }
        } catch (Exception e) {
            log.error("StakeReward find page redis read error: {}", e.getMessage());
        }
        String stakeRewardQueryKey = buildKey("stakeRewardQueryKey", epoch, pageSize, pageNum, orderBy, sorted);
        if (!redisService.setNx(stakeRewardQueryKey, stakeRewardQueryKey + "_Lock", 20, TimeUnit.SECONDS )) {
            throw new RuntimeException("The system is busy, please try again later");
        }
        Sort sort = resolveSort(orderBy, sorted);
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);

        Specification<StakeReward> specification = (root, query, criteriaBuilder) -> {
            if (StringUtils.isNotEmpty(epoch)) {
                return criteriaBuilder.equal(root.get("epoch"), epoch);
            }
            return null;
        };

        Page<StakeReward> page = stakeRewardRepository.findAll(specification, pageable);
        List<StakeReward> content = page.getContent();
        if (epoch.equalsIgnoreCase(currentEpoch)){
            StakeRewardOverview overview = stakingRewardOverviewService.findByEpoch(epoch);
            if (null != overview){
                String validStakingAmountTotal = overview.getValidStakingAmount();
                String currentEpochReward = overview.getCurrentEpochReward();
                for (StakeReward stakeReward : content) {
                    String validStakingAmount = new BigDecimal(stakeReward.getStakingAmount()).multiply(new BigDecimal(stakeReward.getLivingRatio())).setScale(0, RoundingMode.HALF_UP).toString();
                    stakeReward.setValidStakingAmount(validStakingAmount);
                    String validStakingQuota = new BigDecimal(stakeReward.getValidStakingAmount()).divide(new BigDecimal(validStakingAmountTotal),6, RoundingMode.HALF_UP).toString();
                    stakeReward.setValidStakingQuota(validStakingQuota);
                    stakeReward.setStakingReward(new BigDecimal(validStakingQuota).multiply(new BigDecimal(currentEpochReward)).setScale(0, RoundingMode.HALF_UP).toString());
                }
            }
        }
        stakeRewards.addAll(content);
        try {
            if (!stakeRewards.isEmpty()) {
                String pvoStr = JSON.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
                if (epoch.equalsIgnoreCase(currentEpoch)){
                    redisService.set(stakeRewardPageKey, pvoStr, 30, TimeUnit.MINUTES);
                    redisService.set(stakeRewardPageCountKey, String.valueOf(page.getTotalElements()), 30, TimeUnit.MINUTES);
                } else {
                    redisService.set(stakeRewardPageKey, pvoStr, 24, TimeUnit.HOURS);
                    redisService.set(stakeRewardPageCountKey, String.valueOf(page.getTotalElements()), 24, TimeUnit.HOURS);
                }

            }
        } catch (Exception e) {
            log.error("StakeReward find page redis write error: {}", e.getMessage());
        }
        redisService.del(stakeRewardQueryKey);
        return new PageImpl<>(stakeRewards, pageable, page.getTotalElements());
    }

    private String buildKey(String prefix, String epoch, int pageSize, int pageNum, String orderBy, String sorted) {
        StringBuilder keyBuilder = new StringBuilder(prefix);
        keyBuilder.append(":epoch:").append(epoch);
        appendIfNotEmpty(keyBuilder, ":pageSize:", pageSize);
        appendIfNotEmpty(keyBuilder, ":pageNum:", pageNum);
        appendIfNotEmpty(keyBuilder, ":orderBy:", orderBy);
        appendIfNotEmpty(keyBuilder, ":sorted:", sorted);
        return keyBuilder.toString();
    }

    private Sort resolveSort(String orderBy, String sorted) {
        Sort sort;
        if ("livingRatio".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("livingRatio");
        } else if ("stakingAmount".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("stakingAmount");
        } else if ("stakingReward".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("stakingReward");
        } else if ("validStakingAmount".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("validStakingAmount");
        } else if ("validStakingQuota".equalsIgnoreCase(orderBy)) {
            sort = Sort.by("validStakingQuota");
        } else {
            sort = Sort.by("createTime");
        }
        return "DESC".equalsIgnoreCase(sorted) ? sort.descending() : sort.ascending();
    }

    private void appendIfNotEmpty(StringBuilder builder, String suffix, Object value) {
        if (ObjectUtils.isNotEmpty(value)) {
            builder.append(suffix).append(value);
        }
    }


    public Page<StakeReward> findCurrentEpochPageOrderHelper(int pageSize, int pageNum, String orderBy, String sortDirection) {
        String epoch = web3jUtils.getCurrentEpoch();
        String cacheKey = buildCacheKey("currentEpochStakeReward", epoch, pageSize, pageNum, orderBy, sortDirection);
        String countCacheKey = buildCacheKey("currentEpochStakeRewardCount", epoch, pageSize, pageNum, orderBy, sortDirection);

        Map<String, String> result = loadFromCacheOrDatabase(cacheKey, countCacheKey, epoch, pageSize, pageNum, orderBy, sortDirection);
        String rewards = result.get("stakeRewards");
        JSONArray jsonArray = JSONArray.parseArray(rewards.toString());
        List<StakeReward> stakeRewards = JSONArray.parseArray(jsonArray.toJSONString(), StakeReward.class);
        String size = result.getOrDefault("size", "0");
        return new PageImpl<>(stakeRewards, PageRequest.of(pageNum - 1, pageSize), Integer.parseInt(size.toString()));
    }

    private String buildCacheKey(String prefix, String epoch, int pageSize, int pageNum, String orderBy, String sortDirection) {
        StringBuilder sb = new StringBuilder(prefix).append(":epoch:").append(epoch);
        if (pageSize > 0) sb.append(":pageSize:").append(pageSize);
        if (pageNum > 0) sb.append(":pageNum:").append(pageNum);
        if (orderBy != null && !orderBy.isEmpty()) sb.append(":orderBy:").append(orderBy);
        if (sortDirection != null && !sortDirection.isEmpty()) sb.append(":sortDirection:").append(sortDirection);
        return sb.toString();
    }

    private Map<String, String> loadFromCacheOrDatabase(String cacheKey, String countCacheKey, String epoch, int pageSize, int pageNum, String orderBy, String sortDirection) {
        Map<String, String> result = new HashMap<>();
        List<StakeReward> stakeRewards = new ArrayList<>();
        try {

            Object listValue = redisService.get(cacheKey);
            Object countValue = redisService.get(countCacheKey);

            if (listValue != null && countValue != null) {
                JSONArray jsonArray = JSONArray.parseArray(listValue.toString());
                long size = Long.parseLong(countValue.toString());
                result.put("size", String.valueOf(size));
                stakeRewards = JSONArray.parseArray(jsonArray.toJSONString(), StakeReward.class);
                if (!stakeRewards.isEmpty()) {
                    String pvoStr = JSONObject.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
                    result.put("stakeRewards", pvoStr);
                    return result;
                }
            }
        } catch (NumberFormatException e) {
            log.error("Error parsing cache count value", e);
        } catch (Exception e) {
            log.error("Error reading from cache", e);
        }

        return loadFromDatabaseAndCacheResults(cacheKey, countCacheKey, epoch, pageSize, pageNum, orderBy, sortDirection, stakeRewards);
    }

    private Map<String,String> loadFromDatabaseAndCacheResults(String cacheKey, String countCacheKey, String epoch, int pageSize, int pageNum, String orderBy, String sortDirection, List<StakeReward> stakeRewards) {
        String stakeRewardQueryKey = buildCacheKey("stakeRewardCurrentEpochQuery", epoch, pageSize, pageNum, orderBy, sortDirection);
        Boolean b = redisService.setNx(stakeRewardQueryKey, stakeRewardQueryKey + "_Lock", 20, TimeUnit.SECONDS);
        if (!b){
            throw new RuntimeException("The system is busy, please try again later");
        }
        Map<String, String> result = new HashMap<>();
        if (stakeRewards.isEmpty()) {
            try {
                if (stakeRewards.isEmpty()) { // Double-checked locking
                    List<StakeReward> all = findAllByEpoch(epoch);
                    result.put("size", String.valueOf(all.size()));
                    countStakeReward(all, epoch);
                    stakeRewards = pageHelper(pageSize, pageNum, orderBy, sortDirection, all);
                    String pvoStr = JSONObject.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
                    result.put("stakeRewards", pvoStr);
                    cacheResults(cacheKey, countCacheKey, stakeRewards, all.size());
                }
            }catch (Exception e){
                log.error("Error reading from database", e);
            }
        }
        redisService.del(stakeRewardQueryKey);
        return result;
    }

    private void cacheResults(String cacheKey, String countCacheKey, List<StakeReward> stakeRewards, long count) {
        try {
            String pvoStr = JSONObject.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
            redisService.set(cacheKey, pvoStr, 10, TimeUnit.MINUTES);
            redisService.set(countCacheKey, String.valueOf(count), 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error writing to cache", e);
        }
    }

    @NotNull
    private List<StakeReward> pageHelper(int pageSize, int pageNum, String orderBy, String sorted, List<StakeReward> stakeRewards) {
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
            stakeRewards.sort(comparator);
        }

        int endIndex = pageNum * pageSize;
        int size = stakeRewards.size();
        endIndex = Math.min(endIndex, size);
        return stakeRewards.subList((pageNum - 1) * pageSize, endIndex);
    }

    public List<StakeReward> list(String epoch){
        return stakeRewardRepository.findAllByEpoch(epoch);
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

    public List<StakeReward> findAllByEpoch(String epoch){
        List<StakeReward> stakeRewards;
        String stakeRewardEpochKey = "stakeRewardEpoch:" + epoch;
        try {
            Object listValue = redisService.get(stakeRewardEpochKey);
            if (null != listValue) {
                String v = listValue.toString();
                return JSONObject.parseArray(v, StakeReward.class);
            }
        }catch (Exception e){
            log.error("stakeReward findAllByEpoch redis read error：{}", e.getMessage());
        }
        stakeRewards = findAllByEpoch(epoch, 10000);
        if (!stakeRewards.isEmpty()){
            try {
                String pvoStr = JSON.toJSONString(stakeRewards, SerializerFeature.WriteNullStringAsEmpty);
                redisService.set(stakeRewardEpochKey, pvoStr, 5, TimeUnit.MINUTES);
            }catch (Exception e){
                log.error("stakeReward findAllByEpoch redis write error：{}", e.getMessage());
            }
        }
        return stakeRewards;
    }

    public List<StakeReward> findAllByEpoch(String epoch, int batchSize) {
        try {
            List<StakeReward> stakeRewards = new ArrayList<>();
            Pageable pageable = PageRequest.of(0, batchSize);
            Page<StakeReward> currentPage;

            do {
                currentPage = stakeRewardRepository.findAllByEpoch(epoch, pageable);
                stakeRewards.addAll(currentPage.getContent());
                pageable = pageable.next();
            } while (currentPage.hasNext());

            return stakeRewards;
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public boolean checkScanBlockNumber(){
        String currentEpoch = web3jUtils.getCurrentEpoch();
        String currentEpochBlockNumberKey = "currentEpoch" + currentEpoch + "blockNumber";
        BigInteger blockNumber;
        Object object = redisService.get(currentEpochBlockNumberKey);
        if (ObjectUtils.isEmpty(object)){
            blockNumber = web3jUtils.getBlockNumber(0);
            redisService.set("currentEpoch" + currentEpoch + "blockNumber", blockNumber.toString(), 24, TimeUnit.HOURS);
        } else {
            blockNumber = new BigInteger(object.toString());
        }

        ContractOffset contractOffset = contractOffsetService.findByContractAddress("Delay30_BLOCK_CONTRACT_FLAG");
        return contractOffset.getBlockOffset().compareTo(blockNumber) > 0;
    }
}
