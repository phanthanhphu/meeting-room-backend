package org.bsl.meetingroom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.LocalTime;

@ConfigurationProperties(prefix="app.booking")
public class BookingRulesProperties {
    private LocalTime businessStart=LocalTime.of(7,0);
    private LocalTime businessEnd=LocalTime.of(18,0);
    private long minDurationMinutes=15;
    private long maxDurationMinutes=240;
    private long maxAdvanceDays=90;
    public LocalTime getBusinessStart(){return businessStart;} public void setBusinessStart(LocalTime v){businessStart=v;}
    public LocalTime getBusinessEnd(){return businessEnd;} public void setBusinessEnd(LocalTime v){businessEnd=v;}
    public long getMinDurationMinutes(){return minDurationMinutes;} public void setMinDurationMinutes(long v){minDurationMinutes=v;}
    public long getMaxDurationMinutes(){return maxDurationMinutes;} public void setMaxDurationMinutes(long v){maxDurationMinutes=v;}
    public long getMaxAdvanceDays(){return maxAdvanceDays;} public void setMaxAdvanceDays(long v){maxAdvanceDays=v;}
}
