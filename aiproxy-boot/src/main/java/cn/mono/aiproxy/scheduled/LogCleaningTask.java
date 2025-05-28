package cn.mono.aiproxy.scheduled;

import cn.mono.aiproxy.config.AppConfig;
import cn.mono.aiproxy.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogCleaningTask {

    private final LogService logService;
    private final AppConfig appConfig;

    // Example: Run daily at 2 AM. Cron: second minute hour day-of-month month day-of-week
    @Scheduled(cron = "${app.schedule.logCleaningCron:0 0 2 * * ?}")
    public void cleanOldLogs() {
        log.info("Starting scheduled task: CleanOldLogs");
        try {
            Long logStorageHours = appConfig.getLogStorageHours();
            if (logStorageHours != null && logStorageHours > 0) {
                LocalDateTime olderThanTimestamp = LocalDateTime.now(ZoneOffset.UTC).minusHours(logStorageHours);
                log.info("Attempting to delete logs older than: {}", olderThanTimestamp);
                logService.deleteOldLogs(olderThanTimestamp); // This method already logs success/details
                log.info("Log cleaning task: Successfully initiated deletion of logs older than {} hours.", logStorageHours);
            } else {
                log.info("Log cleaning task: Log storage duration is not configured or is zero/negative. Skipping deletion.");
            }

            // TODO: Implement cleaning for RetryLog if that entity/table is ported.
            // Long retryLogStorageHours = appConfig.getRetryLogStorageHours();
            // if (retryLogStorageHours != null && retryLogStorageHours > 0) {
            //     LocalDateTime olderThanRetryTimestamp = LocalDateTime.now(ZoneOffset.UTC).minusHours(retryLogStorageHours);
            //     // logService.deleteOldRetryLogs(olderThanRetryTimestamp);
            //     log.info("RetryLog cleaning: Successfully initiated deletion for retry logs older than {} hours.", retryLogStorageHours);
            // }

        } catch (Exception e) {
            log.error("Error during scheduled log cleaning task: {}", e.getMessage(), e);
        }
        log.info("Finished scheduled task: CleanOldLogs");
    }
}
