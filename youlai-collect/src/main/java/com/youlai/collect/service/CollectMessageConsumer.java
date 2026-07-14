package com.youlai.collect.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class CollectMessageConsumer {

    private final CollectService collectService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${collect.consumer.enabled:true}")
    private boolean enabled;

    @Value("${collect.consumer.batch-size:5}")
    private int batchSize;

    /**
     * 注入采集服务，用于定时消费内部待处理消息。
     */
    public CollectMessageConsumer(CollectService collectService) {
        this.collectService = collectService;
    }

    /**
     * 定时消费待处理采集消息，并通过运行锁避免并发重复消费。
     */
    @Scheduled(fixedDelayString = "${collect.consumer.fixed-delay:5000}")
    public void consumePendingMessages() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            collectService.consumePendingMessages(batchSize);
        } finally {
            running.set(false);
        }
    }
}
