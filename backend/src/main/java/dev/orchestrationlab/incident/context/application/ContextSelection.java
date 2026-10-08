package dev.orchestrationlab.incident.context.application;

import java.util.List;
import java.util.Set;
import dev.orchestrationlab.incident.tool.application.ToolKind;

public record ContextSelection(List<ToolKind> sources, Set<ToolKind> required) {
    public ContextSelection { sources = List.copyOf(sources); required = Set.copyOf(required); }
}
