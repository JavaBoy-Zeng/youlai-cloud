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

    public CollectMessageConsumer(CollectService collectService) {
        this.collectService = collectService;
    }

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
