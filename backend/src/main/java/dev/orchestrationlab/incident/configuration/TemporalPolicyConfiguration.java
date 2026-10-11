package dev.orchestrationlab.incident.configuration;
import java.time.Duration;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.temporal.spring.boot.TemporalOptionsCustomizer;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
/**
 * temporal profilinde zamanlanmış başlatma dispatcher’ını açar ve SDK iletişim sürelerini sınırlar. Bu
 * süreler Activity iş süresi veya kalıcı onay zamanlayıcısıyla aynı değildir.
 */
@Configuration(proxyBeanMethods = false) @Profile("temporal") @EnableScheduling
public class TemporalPolicyConfiguration {
    /**
     * Normal Temporal RPC çağrılarına 5 saniye, görev bekleyen long-poll çağrılarına 10 saniye süre
     * sınırı uygular.
     */
    @Bean TemporalOptionsCustomizer<WorkflowServiceStubsOptions.Builder> temporalRpcDeadline() {
        return builder -> builder.setRpcTimeout(Duration.ofSeconds(5)).setRpcLongPollTimeout(Duration.ofSeconds(10));
    }
}
