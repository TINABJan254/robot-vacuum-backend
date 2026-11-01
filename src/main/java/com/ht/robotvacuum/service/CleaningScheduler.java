package com.ht.robotvacuum.service;

import com.ht.robotvacuum.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CleaningScheduler {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ScheduleRepository schedules;
    private final ScheduledCleaningService cleaning;
    private final Clock clock;
    private ZonedDateTime lastCheckedMinute;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Ho_Chi_Minh")
    public synchronized void checkSchedules() {
        var minute = clock.instant().atZone(ZONE).truncatedTo(ChronoUnit.MINUTES);
        if (minute.equals(lastCheckedMinute)) {
            return;
        }
        lastCheckedMinute = minute;
        for (long id : schedules.findDueIds(minute.toLocalTime(), minute.getDayOfWeek().getValue())) {
            try {
                cleaning.execute(id, minute);
            } catch (RuntimeException ex) {
                // Failure of one robot must not prevent other due schedules from being processed.
                log.warn("Scheduled cleaning failed (scheduleId={}, type={})", id, ex.getClass().getSimpleName());
            }
        }
    }
}
