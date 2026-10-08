package dev.orchestrationlab.incident.configuration;
import java.time.Duration;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.temporal.spring.boot.TemporalOptionsCustomizer;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
@Configuration(proxyBeanMethods = false) @Profile("temporal") @EnableScheduling
public class TemporalPolicyConfiguration {
    @Bean TemporalOptionsCustomizer<WorkflowServiceStubsOptions.Builder> temporalRpcDeadline() {
        return builder -> builder.setRpcTimeout(Duration.ofSeconds(5)).setRpcLongPollTimeout(Duration.ofSeconds(10));
    }
}
