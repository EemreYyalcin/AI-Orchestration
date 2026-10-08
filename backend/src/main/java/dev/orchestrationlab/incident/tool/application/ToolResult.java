package dev.orchestrationlab.incident.tool.application;

import java.util.List;

public record ToolResult(ToolKind source, List<String> evidence, Failure failure) {
    public ToolResult { evidence = List.copyOf(evidence); }
    public enum Failure { FORBIDDEN, INVALID_ARGUMENT, TIMEOUT, UNAVAILABLE, BUSY }
    public boolean successful() { return failure == null; }
    public static ToolResult failed(ToolKind kind, Failure failure) { return new ToolResult(kind, List.of(), failure); }
}
