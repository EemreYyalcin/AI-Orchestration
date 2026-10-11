package dev.orchestrationlab.incident.configuration;
import org.springframework.context.annotation.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.opentracingshim.OpenTracingShim;
/**
 * Temporal’ın OpenTracing entegrasyonunu uygulamanın OpenTelemetry altyapısına bağlar. İş akışı ve
 * Activity çağrılarında izleme bağlamının taşınmasına yardımcı olur.
 */
@Configuration(proxyBeanMethods = false) @Profile("temporal")
public class WorkflowTracingConfiguration {
    /**
     * Mevcut OpenTelemetry nesnesi için OpenTracing uyumluluk katmanı oluşturur; yeni bir dış izleme
     * servisi başlatmaz.
     */
    @Bean io.opentracing.Tracer temporalTracer(OpenTelemetry telemetry) { return OpenTracingShim.createTracerShim(telemetry); }
}
