package dev.orchestrationlab.incident.context.application;

import dev.orchestrationlab.incident.tool.application.ToolKind;

public record Evidence(String id, ToolKind source, String summary) {
    public Evidence {
        if (id == null || id.length() > 64 || source == null || summary == null || summary.isBlank()
                || summary.length() > 10000) throw new IllegalArgumentException("Invalid evidence");
    }
}
