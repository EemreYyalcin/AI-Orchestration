package dev.orchestrationlab.incident.orchestration.application;

import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.context.application.*;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SynthesisService {
    private final IncidentSynthesisModel primary;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    public SynthesisService(IncidentSynthesisModel primary) { this(primary, null); }
    @org.springframework.beans.factory.annotation.Autowired
    public SynthesisService(IncidentSynthesisModel primary, dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        this.primary = primary; this.telemetry = telemetry;
    }
    public Result analyze(String question, EvidenceBundle evidence) {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        IncidentSynthesisModel observed = (q, e) -> {
            if (calls.incrementAndGet() > 1 && telemetry != null) telemetry.count("incident.retries", "synthesis");
            return telemetry == null ? primary.synthesize(q, e) : telemetry.observe("incident.llm", "synthesis", null, () -> primary.synthesize(q, e));
        };
        return analyze(question, evidence, observed, (q, e) -> {
            if (telemetry != null) telemetry.count("incident.model.fallback", "synthesis");
            return new IncidentSynthesis(
                "DEGRADED: model unavailable. Collected evidence requires manual investigation.",
                java.util.List.of(), java.util.List.of("Review collected evidence manually"),
                e.items().stream().map(Evidence::id).collect(Collectors.toSet()), IncidentSynthesis.ProposedAction.NONE);
        });
    }
    public static Result analyze(String question, EvidenceBundle evidence, IncidentSynthesisModel primary, IncidentSynthesisModel fallback) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try { return new Result(validate(primary.synthesize(question, evidence), evidence), false); }
            catch (RuntimeException ignored) { /* bounded retry, no raw provider error leaked */ }
        }
        return new Result(validate(fallback.synthesize(question, evidence), evidence), true);
    }
    private static IncidentSynthesis validate(IncidentSynthesis result, EvidenceBundle evidence) {
        Set<String> ids = evidence.items().stream().map(Evidence::id).collect(Collectors.toSet());
        if (result == null || !ids.containsAll(result.evidenceIds()) || (!ids.isEmpty() && result.evidenceIds().isEmpty()))
            throw new IllegalArgumentException("Invalid evidence citations");
        return result;
    }
    public record Result(IncidentSynthesis synthesis, boolean degraded) { }
}
