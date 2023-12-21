package com.nulink.livingratio.contract.task.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.nulink.livingratio.contract.event.listener.impl.BlockEventListener;

@Component
public class Tasks {

    public static Logger logger = LoggerFactory.getLogger(Tasks.class);

    private static final Object blockListenerTaskKey = new Object();
    private static boolean lockBlockListenerTaskFlag = false;

    private static final Object blockListenerDelay3TaskKey = new Object();
    private static boolean lockBlockListenerDelay3TaskFlag = false;

    private static final Object blockListenerDelayTaskKey = new Object();
    private static boolean lockBlockListenerDelayTaskFlag = false;

    @Autowired
    BlockEventListener blockEventListener;

    @Autowired
    BlockEventListener blockEventDelayListener30;

    @Async
    @Scheduled(cron = "0/6 * * * * ?")
    public void scanBlockEvent() {


        synchronized (blockListenerTaskKey) {
            if (Tasks.lockBlockListenerTaskFlag) {
                logger.warn("The blockchain event scanning task is already in progress");
                return;
            }
            Tasks.lockBlockListenerTaskFlag = true;
        }

        logger.info("Commence the execution of the blockchain event scanning task.");
        try {
            blockEventListener.start(0, null, null);

        } catch (Exception e) {
            e.printStackTrace();
        }

        Tasks.lockBlockListenerTaskFlag = false;

        logger.info("The Delay0 blockchain event scanning task has concluded.");
    }

    @Async
    @Scheduled(cron = "0/6 * * * * ?")
    public void scanBlockEventDelay30() {

        synchronized (blockListenerDelayTaskKey) {
            if (Tasks.lockBlockListenerDelayTaskFlag) {
                logger.warn("The Delay30 blockchain event scanning task is currently in progress.");
                return;
            }
            Tasks.lockBlockListenerDelayTaskFlag = true;
        }

        logger.info("Initiate the execution of the Delay30 blockchain event scanning task.");
        try {

            blockEventDelayListener30.start(30, null, null);

        } catch (Exception e) {
            e.printStackTrace();
        }

        Tasks.lockBlockListenerDelayTaskFlag = false;

        logger.info("The Delay30 blockchain event scanning task has concluded.");
    }

}
