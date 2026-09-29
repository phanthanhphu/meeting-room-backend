package org.bsl.meetingroom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.*;

@Configuration
@EnableScheduling
@EnableMongoAuditing
@EnableConfigurationProperties(BookingRulesProperties.class)
public class AppConfig {
    @Bean Clock appClock(@Value("${app.time-zone:Asia/Ho_Chi_Minh}") String zone){
        return Clock.system(ZoneId.of(zone));
    }
}
