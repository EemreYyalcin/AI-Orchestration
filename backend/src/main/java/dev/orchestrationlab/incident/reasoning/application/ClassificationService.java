package dev.orchestrationlab.incident.reasoning.application;

import org.springframework.stereotype.Service;
import dev.orchestrationlab.incident.orchestration.application.WorkflowRoutingPolicy;

@Service
public class ClassificationService {
    private final IncidentClassificationModel model;
    private final WorkflowRoutingPolicy routes;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    public ClassificationService(IncidentClassificationModel model, WorkflowRoutingPolicy routes) { this(model, routes, null); }
    @org.springframework.beans.factory.annotation.Autowired
    public ClassificationService(IncidentClassificationModel model, WorkflowRoutingPolicy routes, dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        this.telemetry = telemetry;
        this.model = model; this.routes = routes;
    }
    public Result classify(String question) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (attempt > 0 && telemetry != null) telemetry.count("incident.retries", "classification");
                var value = telemetry == null ? model.classify(question) : telemetry.observe("incident.llm", "classification", null, () -> model.classify(question));
                return new Result(value, routes.route(value), false);
            } catch (RuntimeException invalidOrUnavailable) { /* bounded read-only retry */ }
        }
        var fallback = IncidentClassification.fallback();
        return new Result(fallback, routes.route(fallback), true);
    }
    public record Result(IncidentClassification classification, WorkflowRoutingPolicy.Route route, boolean degraded) { }
}
