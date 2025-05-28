package cn.mono.aiproxy.scheduled;

import cn.mono.aiproxy.service.ChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChannelBalanceUpdateTask {

    private final ChannelService channelService;

    @Scheduled(fixedRateString = "${app.schedule.updateChannelBalanceRateMinutes:600000}", initialDelay = 300000) // Default 10 min, initial delay 5 min
    public void performChannelBalanceUpdate() {
        log.info("Starting scheduled task: ChannelBalanceUpdateTask");
        try {
            channelService.updateAllChannelsBalance();
            log.info("ChannelBalanceUpdateTask: Successfully called updateAllChannelsBalance.");
        } catch (Exception e) {
            log.error("Error during scheduled ChannelBalanceUpdateTask: {}", e.getMessage(), e);
        }
        log.info("Finished scheduled task: ChannelBalanceUpdateTask");
    }
}
