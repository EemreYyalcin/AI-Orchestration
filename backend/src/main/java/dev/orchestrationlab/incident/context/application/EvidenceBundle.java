package dev.orchestrationlab.incident.context.application;

import java.util.List;
import dev.orchestrationlab.incident.tool.application.*;

public record EvidenceBundle(List<Evidence> items, List<SourceFailure> failures, boolean truncated) {
    public EvidenceBundle { items = List.copyOf(items); failures = List.copyOf(failures); }
    public record SourceFailure(ToolKind source, ToolResult.Failure failure) { }
    public boolean degraded() { return truncated || !failures.isEmpty(); }
}
