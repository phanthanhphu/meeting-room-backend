package org.bsl.meetingroom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.TaskScheduler;
import java.time.*;

@Configuration
@EnableScheduling
@EnableMongoAuditing
@EnableConfigurationProperties(BookingRulesProperties.class)
public class AppConfig {
    /** Separate scheduling threads keep mail delivery from waiting behind long booking cleanup jobs. */
    @Bean TaskScheduler taskScheduler(){
        ThreadPoolTaskScheduler scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("meeting-jobs-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
    @Bean Clock appClock(@Value("${app.time-zone:Asia/Ho_Chi_Minh}") String zone){
        return Clock.system(ZoneId.of(zone));
    }
}
