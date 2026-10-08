package dev.orchestrationlab.incident.reasoning.application;

import java.util.Set;
import dev.orchestrationlab.incident.tool.application.ToolKind;

public record IncidentClassification(IncidentType incidentType, Severity severity,
                                     Set<ToolKind> requiredTools, String summary) {
    public IncidentClassification {
        if (incidentType == null || severity == null || requiredTools == null || requiredTools.isEmpty()
                || requiredTools.size() > 4 || summary == null || summary.isBlank() || summary.length() > 1000)
            throw new IllegalArgumentException("Invalid incident classification");
        requiredTools = Set.copyOf(requiredTools);
    }
    public static IncidentClassification fallback() {
        return new IncidentClassification(IncidentType.UNKNOWN, Severity.LOW,
                Set.of(ToolKind.LOGS, ToolKind.DOCUMENTATION), "Classification unavailable; use conservative diagnostics");
    }
}
