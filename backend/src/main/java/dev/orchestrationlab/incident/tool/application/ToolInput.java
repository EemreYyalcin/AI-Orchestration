package dev.orchestrationlab.incident.tool.application;

/** A predefined diagnostic capability, never SQL, a URL or executable code. */
public record ToolInput(String service, Topic topic, int limit) {
    public enum Topic { ERRORS, CONNECTIONS, TIMEOUTS, RUNBOOK, LATENCY }
    public void validate() {
        if (!java.util.Set.of("recommendation", "payments").contains(service == null ? "" : service)
                || topic == null || limit < 1 || limit > 10)
            throw new IllegalArgumentException("Invalid diagnostic input");
    }
}
