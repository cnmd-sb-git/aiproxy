package cn.mono.aiproxy.scheduled;

import cn.mono.aiproxy.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DetectIPGroupsTask {

    private final LogService logService;
    // AppConfig is injected into LogService, so LogService can access thresholds directly.

    @Scheduled(fixedRateString = "${app.schedule.detectIpGroupsRateMinutes:60000}", initialDelayString = "${app.schedule.detectIpGroupsInitialDelayMinutes:120000}") // Default 1 min rate, 2 min initial delay
    public void performDetectIPGroups() {
        log.info("Starting scheduled task: DetectIPGroupsTask");
        try {
            logService.detectAndProcessIPGroups();
            // The LogService method itself logs details including thresholds from AppConfig
            log.info("DetectIPGroupsTask: Successfully called detectAndProcessIPGroups.");
        } catch (Exception e) {
            log.error("Error during scheduled DetectIPGroupsTask: {}", e.getMessage(), e);
        }
        log.info("Finished scheduled task: DetectIPGroupsTask");
    }
}
