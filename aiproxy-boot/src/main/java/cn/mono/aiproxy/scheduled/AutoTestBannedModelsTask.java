package cn.mono.aiproxy.scheduled;

import cn.mono.aiproxy.service.ChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutoTestBannedModelsTask {

    private final ChannelService channelService;

    @Scheduled(fixedRateString = "${app.schedule.autoTestBannedModelsRateSeconds:1800000}", initialDelay = 600000) // Default 30 min, initial delay 10 min
    public void performAutoTestBannedModels() {
        log.info("Starting scheduled task: AutoTestBannedModelsTask");
        try {
            channelService.autoTestBannedModels();
            log.info("AutoTestBannedModelsTask: Successfully called autoTestBannedModels.");
        } catch (Exception e) {
            log.error("Error during scheduled AutoTestBannedModelsTask: {}", e.getMessage(), e);
        }
        log.info("Finished scheduled task: AutoTestBannedModelsTask");
    }
}
