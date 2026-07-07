package com.youlai.flowable.scheduler;

import com.youlai.flowable.service.impl.AppBuilderAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppBuilderAutomationScheduler {

    private final AppBuilderAutomationService automationService;

    @Scheduled(fixedDelayString = "${app-builder.automation.schedule-fixed-delay:60000}")
    public void executeScheduleRules() {
        automationService.executeScheduleTriggers();
    }
}
