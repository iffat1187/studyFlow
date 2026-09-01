package com.studyflow.studyflow.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Central application timezone configuration.
 *
 * StudyFlow is intended for students using their local time, so every date
 * calculation ({@code LocalDate.now()}, {@code LocalTime.now()}) and the
 * scheduled notification job must agree on a single timezone.
 *
 * The value is read once from {@code studyflow.timezone} (defaulting to the
 * JVM/system timezone when unset or invalid) and applied as the JVM default
 * at startup. This keeps the schedule trigger, the generation service's
 * "today" derivation and every other date operation in the same zone, and
 * avoids scattering timezone strings across the codebase.
 */
@Configuration
public class TimeZoneConfig {

    private static final Logger log = LoggerFactory.getLogger(TimeZoneConfig.class);

    private final String timezone;

    /**
     * The configured application timezone identifier, e.g.
     * {@code Asia/Dhaka}. Falls back to the system default when blank.
     *
     * @param timezone the value of {@code studyflow.timezone}
     */
    public TimeZoneConfig(@Value("${studyflow.timezone:}") String timezone) {
        this.timezone = timezone;
    }

    /**
     * Apply the intended application timezone as the JVM default.
     */
    @PostConstruct
    public void applyTimezone() {
        if (timezone == null || timezone.isBlank()) {
            log.info("studyflow.timezone not set; using system timezone {}.",
                    TimeZone.getDefault().getID());
            return;
        }
        TimeZone zone = TimeZone.getTimeZone(timezone);
        TimeZone.setDefault(zone);
        log.info("StudyFlow timezone set to {}.", zone.getID());
    }
}
