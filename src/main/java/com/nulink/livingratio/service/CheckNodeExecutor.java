package com.nulink.livingratio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nulink.livingratio.utils.HttpClientUtil;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Log4j2
@Service
public class CheckNodeExecutor {

    private static String CHECK_RUNNING = "/check-running";

    private ExecutorService executor;
    private CompletionService<ServerStatus> completionService;

    public CheckNodeExecutor() {
        executor = Executors.newFixedThreadPool(40);
        completionService = new ExecutorCompletionService<>(executor);
    }

    public List<ServerStatus> executePingTasks(List<ServerStatus> serverStatuses) {
        List<Future<ServerStatus>> futures = new ArrayList<>();

        for (ServerStatus status : serverStatuses) {
            Future<ServerStatus> future = completionService.submit(new ServerStatusTask(status));
            futures.add(future);
        }

        List<ServerStatus> results = new ArrayList<>();
        for (int i = 0; i < serverStatuses.size(); i++) {
            try {
                Future<ServerStatus> completedFuture = completionService.take();
                ServerStatus result = completedFuture.get();
                results.add(result);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                results.add(null);
            }
        }
        return results;
    }

    public static class ServerStatus {
        private String server;
        private boolean online;

        private String stakingProvider;

        public ServerStatus(String server, boolean online) {
            this.server = server;
            this.online = online;
        }

        public ServerStatus(String server, String stakingProvider) {
            this.server = server;
            this.stakingProvider = stakingProvider;
        }

        public String getServer() {
            return server;
        }

        public boolean isOnline() {
            return online;
        }

        public void setServer(String server) {
            this.server = server;
        }

        public void setOnline(boolean online) {
            this.online = online;
        }

        public String getStakingProvider() {
            return stakingProvider;
        }

        public void setStakingProvider(String stakingProvider) {
            this.stakingProvider = stakingProvider;
        }
    }

    static class ServerStatusTask implements Callable<ServerStatus> {
        private ServerStatus serverStatus;

        public ServerStatusTask(ServerStatus serverStatus) {
            this.serverStatus = serverStatus;
        }

        @Override
        public ServerStatus call() throws Exception {
            boolean isOnline = isServerRunning(serverStatus);
            serverStatus.setOnline(isOnline);
            return serverStatus;
        }

        private boolean isServerRunning(ServerStatus serverStatus) {

            OkHttpClient client = HttpClientUtil.getUnsafeOkHttpClient();
            HttpUrl.Builder urlBuilder = HttpUrl.parse(serverStatus.getServer() + CHECK_RUNNING).newBuilder();
            urlBuilder.addQueryParameter("staker_address", serverStatus.getStakingProvider());
            String url = urlBuilder.build().toString();

            Response response;
            boolean result;
            try {
                Request request = new Request.Builder().url(url).build();
                response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    /*
                        success response:
                       {
                            "version":"0.5.0"
                            "data": "success"
                        }
                    */
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = null;
                    if (response.body() != null) {
                        jsonNode = objectMapper.readTree(response.body().string());
                        if (jsonNode.has("data")){
                            result = true;
                        } else {
                            result = false;
                        }
                    } else {
                        result = false;
                    }
                } else {
                    log.error(serverStatus.getServer() +" Request failed. Response code: " + response.code());
                    result = false;
                }
                response.close();
            } catch (Exception e) {
                log.error(serverStatus.getServer() + " connect failure:" + e.getMessage());
                result = false;
            }
            return result;
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

}
