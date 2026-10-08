package dev.orchestrationlab.incident.configuration;
import org.springframework.context.annotation.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.opentracingshim.OpenTracingShim;
@Configuration(proxyBeanMethods = false) @Profile("temporal")
public class WorkflowTracingConfiguration {
    @Bean io.opentracing.Tracer temporalTracer(OpenTelemetry telemetry) { return OpenTracingShim.createTracerShim(telemetry); }
}
