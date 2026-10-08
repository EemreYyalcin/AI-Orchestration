package dev.orchestrationlab.incident.investigation;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.UUID;
import dev.orchestrationlab.incident.investigation.application.InvestigationStartLimiter;
import static org.assertj.core.api.Assertions.*;
class RateLimitTest {
    @Test void realRedisIsAtomicPerUserAndExpires() {
        try (var container = new GenericContainer<>("redis:8").withExposedPorts(6379)) {
            container.start();
            var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(container.getHost(), container.getMappedPort(6379)));
            factory.afterPropertiesSet(); factory.start();
            try {
                var redis = new StringRedisTemplate(factory); var limiter = new InvestigationStartLimiter(redis, true, 2, Duration.ofMillis(300));
                UUID owner = UUID.randomUUID(); limiter.acquire(owner); limiter.acquire(owner);
                assertThatThrownBy(() -> limiter.acquire(owner)).isInstanceOf(ResponseStatusException.class)
                        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(429));
                limiter.acquire(UUID.randomUUID());
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> !Boolean.TRUE.equals(redis.hasKey("investigation-start:v1:" + owner)));
                limiter.acquire(owner);
            } finally { factory.destroy(); }
        }
    }
    @Test void unavailableRedisFailsClosedBeforeModelWork() {
        var redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        org.mockito.Mockito.when(redis.execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.script.RedisScript.class),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(Object[].class))).thenThrow(new IllegalStateException("secret connection details"));
        assertThatThrownBy(() -> new InvestigationStartLimiter(redis, true, 2, Duration.ofMinutes(1)).acquire(UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("secret");
    }
}
