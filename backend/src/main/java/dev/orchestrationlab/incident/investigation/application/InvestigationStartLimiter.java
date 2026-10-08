package dev.orchestrationlab.incident.investigation.application;

import java.util.*;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class InvestigationStartLimiter {
    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int limit;
    private final Duration window;
    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]); if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; return n", Long.class);
    public InvestigationStartLimiter(StringRedisTemplate redis, @Value("${app.rate-limit.enabled:false}") boolean enabled,
            @Value("${app.rate-limit.limit:5}") int limit, @Value("${app.rate-limit.window:PT1M}") Duration window) {
        if (limit < 1 || limit > 100 || window.isNegative() || window.isZero() || window.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Invalid rate limit policy");
        this.redis = redis; this.enabled = enabled; this.limit = limit; this.window = window;
    }
    public void acquire(UUID owner) {
        if (!enabled) return;
        Long count;
        try { count = redis.execute(script, List.of("investigation-start:v1:" + owner), Long.toString(window.toMillis())); }
        catch (RuntimeException unavailable) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Investigation admission unavailable"); }
        if (count == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Investigation admission unavailable");
        if (count > limit) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Investigation start limit reached; retry after the configured window");
    }
}
