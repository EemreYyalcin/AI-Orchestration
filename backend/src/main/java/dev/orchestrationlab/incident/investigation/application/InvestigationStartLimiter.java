package dev.orchestrationlab.incident.investigation.application;

import java.util.*;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * İnceleme oluşturma ve yeniden başlatma için kullanıcı başına isteğe bağlı Redis kabul kontrolüdür.
 * Lua ile sayaç artırma ve ilk istekte süre atama atomiktir; reddedilen istekler pencereyi uzatmaz.
 * Redis oturum veya kalıcı iş akışı deposu değildir.
 */
@Service
public class InvestigationStartLimiter {
    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int limit;
    private final Duration window;
    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]); if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; return n", Long.class);
    /**
     * Redis bağımlılığı ve kabul ayarlarını bağlar. Limiti 1–100, pencereyi pozitif/en fazla bir saat
     * kabul eder; geçersiz politika başlangıçta reddedilir.
     */
    public InvestigationStartLimiter(StringRedisTemplate redis, @Value("${app.rate-limit.enabled:false}") boolean enabled,
            @Value("${app.rate-limit.limit:5}") int limit, @Value("${app.rate-limit.window:PT1M}") Duration window) {
        if (limit < 1 || limit > 100 || window.isNegative() || window.isZero() || window.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Invalid rate limit policy");
        this.redis = redis; this.enabled = enabled; this.limit = limit; this.window = window;
    }
    /**
     * Kontrol kapalıysa doğrudan geçer. Açıksa dahili UUID anahtarındaki sayacı artırır; limit
     * aşıldığında 429, Redis erişilemez veya sonuç belirsizse 503 verir. Arızada kapalı davranarak
     * model/iş akışı maliyeti başlamadan isteği durdurur.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     */
    public void acquire(UUID owner) {
        if (!enabled) return;
        Long count;
        try { count = redis.execute(script, List.of("investigation-start:v1:" + owner), Long.toString(window.toMillis())); }
        catch (RuntimeException unavailable) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Investigation admission unavailable"); }
        if (count == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Investigation admission unavailable");
        if (count > limit) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Investigation start limit reached; retry after the configured window");
    }
}
