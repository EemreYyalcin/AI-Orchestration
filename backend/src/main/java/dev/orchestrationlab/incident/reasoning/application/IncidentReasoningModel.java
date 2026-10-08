package dev.orchestrationlab.incident.reasoning.application;

import dev.orchestrationlab.incident.tool.application.ToolSession;

/** Provider-neutral application boundary. */
public interface IncidentReasoningModel {
    String reason(String question, ToolSession tools);
}
