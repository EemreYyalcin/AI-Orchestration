package dev.orchestrationlab.incident.reasoning.infrastructure;

import dev.orchestrationlab.incident.reasoning.application.IncidentReasoningModel;
import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.tool.application.*;

/** Deterministic simulation, labelled explicitly so disabled AI cannot masquerade as live analysis. */
public class FakeIncidentReasoningModel implements IncidentReasoningModel, IncidentClassificationModel, IncidentSynthesisModel {
    @Override public IncidentSynthesis synthesize(String question, dev.orchestrationlab.incident.context.application.EvidenceBundle evidence) {
        return new IncidentSynthesis("SIMULATED investigation: bounded evidence was collected; verify findings against real systems.",
                java.util.List.of("SIMULATED: connection pressure or dependency errors require operator review"),
                java.util.List.of("Review cited evidence before any production action"),
                evidence.items().stream().map(dev.orchestrationlab.incident.context.application.Evidence::id).collect(java.util.stream.Collectors.toSet()),
                question.toLowerCase(java.util.Locale.ROOT).contains("recommendation")
                        ? IncidentSynthesis.ProposedAction.RESTART_RECOMMENDATION_SERVICE : IncidentSynthesis.ProposedAction.NONE);
    }
    @Override public IncidentClassification classify(String question) {
        String text = question.toLowerCase(java.util.Locale.ROOT);
        IncidentType type = text.contains("database") || text.contains("connection") || text.contains("pool") ? IncidentType.DATABASE
                : text.contains("null pointer") || text.contains("nullpointer") ? IncidentType.APPLICATION
                : text.contains("timeout") || text.contains("dependency") ? IncidentType.DEPENDENCY
                : text.contains("latency") || text.contains("slow") ? IncidentType.PERFORMANCE : IncidentType.UNKNOWN;
        var source = type == IncidentType.DATABASE ? ToolKind.DATABASE
                : type == IncidentType.PERFORMANCE ? ToolKind.METRICS : ToolKind.LOGS;
        return new IncidentClassification(type, type == IncidentType.UNKNOWN ? Severity.LOW : Severity.HIGH,
                java.util.Set.of(source, ToolKind.DOCUMENTATION), "SIMULATED classification: " + type);
    }
    @Override public String reason(String question, ToolSession tools) {
        var input = new ToolInput("recommendation", ToolInput.Topic.ERRORS, 3);
        var kind = tools.allows(ToolKind.LOGS) ? ToolKind.LOGS : ToolKind.DOCUMENTATION;
        var result = tools.call(kind, input); // Simulated model request → Java execution → simulated next turn.
        return "SIMULATED analysis: " + (result.successful() ? String.join("; ", result.evidence())
                : "Evidence unavailable: " + result.failure());
    }
}
