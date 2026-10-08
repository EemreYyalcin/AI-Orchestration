package dev.orchestrationlab.incident.tool.application;

import java.util.Set;
import java.util.UUID;

/** Created by Java from authenticated ownership and workflow policy, never model arguments. */
public record ToolContext(UUID ownerId, UUID investigationId, Set<ToolKind> allowedTools) {
    public ToolContext {
        java.util.Objects.requireNonNull(ownerId);
        java.util.Objects.requireNonNull(investigationId);
        allowedTools = Set.copyOf(allowedTools);
    }
}
