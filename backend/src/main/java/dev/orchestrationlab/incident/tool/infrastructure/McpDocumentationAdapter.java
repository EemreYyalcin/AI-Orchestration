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

/**
 * Paketlenmiş runbook kanıtına resmi Streamable HTTP MCP istemcisiyle erişir. Yönetici URL’si
 * doğrulanır; uzak keşif yetki vermez ve yalnızca searchDocumentation çağrılır. Her okumada bağlantı
 * kurulduğundan isteğe bağlı MCP kesintisi backend başlangıcını engellemez.
 */
public final class McpDocumentationAdapter implements EvidenceAdapter {
    private final String baseUrl;
    private final io.opentelemetry.api.OpenTelemetry tracing;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    /**
     * Yapılandırılmış URL’nin http/https ve host içerdiğini; userinfo/query/fragment içermediğini
     * doğrular. İzleme ve telemetri isteğe bağlıdır; kısa sürüm bunları kapatır. Bu kontrol genel host
     * izin listesi değildir.
     */
    public McpDocumentationAdapter(String baseUrl) {
        this(baseUrl, null, null);
    }
    /**
     * Yapılandırılmış URL’nin http/https ve host içerdiğini; userinfo/query/fragment içermediğini
     * doğrular. İzleme ve telemetri isteğe bağlıdır; kısa sürüm bunları kapatır. Bu kontrol genel host
     * izin listesi değildir.
     */
    public McpDocumentationAdapter(String baseUrl, io.opentelemetry.api.OpenTelemetry tracing,
            dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        var uri = URI.create(baseUrl);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Invalid configured MCP URL");
        this.baseUrl = baseUrl;
        this.tracing = tracing; this.telemetry = telemetry;
    }
    /**
     * Bu adaptörün yalnızca DOCUMENTATION yeteneğini sağladığını bildirir.
     */
    @Override public ToolKind kind() { return ToolKind.DOCUMENTATION; }
    /**
     * Runbook okumasını varsa MCP gözlemi içinde yürütür; hata ToolExecutionService katmanında güvenli
     * kaynak hatasına dönüştürülür.
     */
    @Override public List<String> read(ToolInput input) {
        return telemetry == null ? readInternal(input) : telemetry.observe("incident.mcp", "documentation", null, () -> readInternal(input));
    }
    /**
     * Girdiyi doğrular, izleme başlıklarını taşır, 1 saniyelik initialize/istek sınırıyla MCP
     * istemcisini açar. Keşifte sabit aracı doğrular ve yalnızca onu çağırır. isError/12000 karakteri
     * aşan metni reddeder, JSON listesini ayrıştırır ve toplam öğe sayısını input.limit ile sınırlar;
     * bağlantıyı kapatır.
     */
    private List<String> readInternal(ToolInput input) {
        input.validate();
        var request = java.net.http.HttpRequest.newBuilder();
        if (tracing != null) tracing.getPropagators().getTextMapPropagator().inject(io.opentelemetry.context.Context.current(), request,
                (builder, key, value) -> builder.header(key, value));
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl).requestBuilder(request).build();
        // Bağlantı her çağrıda açılır; isteğe bağlı MCP kesintisi uygulama başlangıcını engellemez.
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
