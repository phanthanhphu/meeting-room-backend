package org.bsl.meetingroom.security;

import org.bsl.meetingroom.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Locale;

/** Small single-instance safety net. Prefer a shared limiter at the gateway for clustered deployments. */
@Service
public class LoginAttemptService {
    private static final int MAX_FAILURES=5;
    private static final Duration WINDOW=Duration.ofMinutes(15);
    private record Entry(int failures, Instant resetAt) {}
    private final Map<String,Entry> attempts=new HashMap<>();

    public String key(String remoteAddress,String identifier) {
        String ip=remoteAddress==null?"unknown":remoteAddress;
        String user=identifier==null?"":identifier.strip().toLowerCase(Locale.ROOT);
        return ip+"|"+user;
    }
    public synchronized void check(String key) {
        clean();
        Entry entry=attempts.get(key);
        if(entry!=null && entry.failures()>=MAX_FAILURES)
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS,"LOGIN_RATE_LIMIT",
                    "Too many failed logins. Please try again in 15 minutes.");
    }
    public synchronized void failed(String key) {
        Instant now=Instant.now();
        Entry current=attempts.get(key);
        if(current==null || !current.resetAt().isAfter(now)) attempts.put(key,new Entry(1,now.plus(WINDOW)));
        else attempts.put(key,new Entry(current.failures()+1,current.resetAt()));
        if(attempts.size()>10000) clean();
    }
    public synchronized void succeeded(String key) { attempts.remove(key); }
    private void clean() {
        Instant now=Instant.now();
        Iterator<Entry> entries=attempts.values().iterator();
        while(entries.hasNext()) if(!entries.next().resetAt().isAfter(now)) entries.remove();
        // Prevent unbounded memory under identifier-spraying; gateway limiter is still recommended.
        if(attempts.size()>15000) attempts.clear();
    }
}
