package dev.orchestrationlab.incident.orchestration.application;

import java.util.*;
import org.springframework.stereotype.Component;
import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.tool.application.ToolKind;

@Component
public class WorkflowRoutingPolicy {
    public Route route(IncidentClassification classification) {
        Set<ToolKind> allowed = switch (classification.incidentType()) {
            case DATABASE -> Set.of(ToolKind.LOGS, ToolKind.DATABASE, ToolKind.DOCUMENTATION, ToolKind.METRICS);
            case APPLICATION, UNKNOWN -> Set.of(ToolKind.LOGS, ToolKind.DOCUMENTATION);
            case DEPENDENCY, PERFORMANCE -> Set.of(ToolKind.LOGS, ToolKind.METRICS, ToolKind.DOCUMENTATION);
        };
        if (!allowed.containsAll(classification.requiredTools())) throw new IllegalArgumentException("Tool outside workflow policy");
        ToolKind required = switch (classification.incidentType()) {
            case DATABASE -> ToolKind.DATABASE;
            case PERFORMANCE -> ToolKind.METRICS;
            default -> ToolKind.LOGS;
        };
        var selected = EnumSet.copyOf(classification.requiredTools());
        selected.add(required);
        selected.add(ToolKind.DOCUMENTATION);
        return new Route(Set.copyOf(selected), Set.of(required));
    }
    public record Route(Set<ToolKind> tools, Set<ToolKind> requiredEvidence) {
        public Route { tools = Set.copyOf(tools); requiredEvidence = Set.copyOf(requiredEvidence); }
    }
}
