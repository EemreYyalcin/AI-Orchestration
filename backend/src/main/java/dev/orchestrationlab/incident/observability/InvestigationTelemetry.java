package dev.orchestrationlab.incident.observability;

import java.util.UUID;
import java.util.function.Supplier;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.*;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import dev.orchestrationlab.incident.tool.application.ToolResult;

@Component
public class InvestigationTelemetry {
    private final MeterRegistry meters;
    private final ObservationRegistry observations;
    private final String provider, model;
    public InvestigationTelemetry(MeterRegistry meters, ObservationRegistry observations, Environment env) {
        this.meters = meters; this.observations = observations;
        provider = env.acceptsProfiles(Profiles.of("ai")) ? "openai" : "simulation";
        model = env.getProperty("AI_CHAT_MODEL", "offline-fixture");
    }
    public <T> T observe(String operation, String category, UUID id, Supplier<T> work) {
        var observation = Observation.createNotStarted(operation, observations).lowCardinalityKeyValue("category", category);
        if (operation.equals("incident.llm")) observation.lowCardinalityKeyValue("provider", provider)
                .lowCardinalityKeyValue("model", model).lowCardinalityKeyValue("prompt.version", "v1");
        if (id != null) observation.highCardinalityKeyValue("investigation.id", id.toString());
        return observation.observe(work);
    }
    public void tool(ToolResult result, long nanos) {
        String outcome = result.successful() ? "success" : result.failure().name().toLowerCase(java.util.Locale.ROOT);
        meters.counter("incident.tool.calls", "tool", result.source().name(), "outcome", outcome).increment();
        meters.timer("incident.tool.duration", "tool", result.source().name(), "outcome", outcome).record(Duration.ofNanos(nanos));
        if (!result.successful()) meters.counter("incident.tool.failures", "tool", result.source().name(), "reason", outcome).increment();
        if (result.failure() == ToolResult.Failure.TIMEOUT) count("incident.timeouts", "tool");
    }
    public void count(String metric, String stage) { meters.counter(metric, "stage", stage).increment(); }
    public void completed(String outcome, Duration duration) {
        meters.counter("incident.investigations", "outcome", outcome).increment();
        meters.timer("incident.investigation.duration", "outcome", outcome).record(duration);
    }
    public void approval(Duration duration, String outcome) { meters.timer("incident.approval.wait", "decision", outcome).record(duration); }
}
