package dev.orchestrationlab.incident.tool.infrastructure;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import dev.orchestrationlab.incident.tool.application.*;

/** Remote discovery is not authorization: only the known runbook tool is used. */
public final class McpDocumentationAdapter implements EvidenceAdapter {
    private final String baseUrl;
    private final io.opentelemetry.api.OpenTelemetry tracing;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    public McpDocumentationAdapter(String baseUrl) {
        this(baseUrl, null, null);
    }
    public McpDocumentationAdapter(String baseUrl, io.opentelemetry.api.OpenTelemetry tracing,
            dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        var uri = URI.create(baseUrl);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Invalid configured MCP URL");
        this.baseUrl = baseUrl;
        this.tracing = tracing; this.telemetry = telemetry;
    }
    @Override public ToolKind kind() { return ToolKind.DOCUMENTATION; }
    @Override public List<String> read(ToolInput input) {
        return telemetry == null ? readInternal(input) : telemetry.observe("incident.mcp", "documentation", null, () -> readInternal(input));
    }
    private List<String> readInternal(ToolInput input) {
        input.validate();
        var request = java.net.http.HttpRequest.newBuilder();
        if (tracing != null) tracing.getPropagators().getTextMapPropagator().inject(io.opentelemetry.context.Context.current(), request,
                (builder, key, value) -> builder.header(key, value));
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl).requestBuilder(request).build();
        // Lazy per-call connection: optional MCP failure never prevents application startup.
        try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(1))
                .initializationTimeout(Duration.ofSeconds(1)).build()) {
            client.initialize();
            if (client.listTools().tools().stream().noneMatch(tool -> tool.name().equals("searchDocumentation")))
                throw new IllegalStateException("Required MCP capability missing");
            var result = client.callTool(new McpSchema.CallToolRequest("searchDocumentation", Map.of("input",
                    Map.of("service", input.service(), "topic", input.topic().name(), "limit", input.limit()))));
            if (Boolean.TRUE.equals(result.isError())) throw new IllegalStateException("MCP documentation call failed");
            var evidence = new ArrayList<String>();
            for (var content : result.content()) {
                if (content instanceof McpSchema.TextContent text) {
                    if (text.text().length() > 12000) throw new IllegalStateException("Oversized MCP result");
                    List<String> items = JsonMapper.builder().build().readValue(text.text(), new TypeReference<List<String>>() { });
                    evidence.addAll(items.stream().limit(input.limit()).toList());
                }
            }
            return evidence.stream().limit(input.limit()).toList();
        }
    }
}
