package dev.orchestrationlab.incident.tool.application;

import java.util.concurrent.atomic.AtomicInteger;

/** Per reasoning call: the model cannot expand the allowlist or call budget. */
public final class ToolSession {
    private final ToolExecutionService execution;
    private final ToolContext context;
    private final AtomicInteger calls = new AtomicInteger();
    public ToolSession(ToolExecutionService execution, ToolContext context) {
        this.execution = execution;
        this.context = context;
    }
    public ToolResult call(ToolKind kind, ToolInput input) {
        if (calls.incrementAndGet() > 8) throw new IllegalStateException("Tool call budget exceeded");
        return execution.execute(context, kind, input);
    }
    public boolean allows(ToolKind kind) { return context.allowedTools().contains(kind); }
    public int callCount() { return calls.get(); }
}
