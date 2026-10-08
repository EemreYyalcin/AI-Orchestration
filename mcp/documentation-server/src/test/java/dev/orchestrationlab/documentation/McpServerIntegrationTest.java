package dev.orchestrationlab.documentation;

import java.time.Duration;
import java.util.Map;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerIntegrationTest {
    @LocalServerPort int port;
    @Test void realStreamableClientDiscoversAndCallsLocalCorpus() {
        try (var client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                .requestTimeout(Duration.ofSeconds(5)).build()) {
            var initialization = client.initialize();
            assertThat(initialization.serverInfo().name()).isEqualTo("documentation-runbooks");
            assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name).containsExactly("searchDocumentation");
            var response = client.callTool(new McpSchema.CallToolRequest("searchDocumentation",
                    Map.of("input", Map.of("service", "recommendation", "topic", "CONNECTIONS", "limit", 2))));
            assertThat(response.isError()).isFalse();
            assertThat(response.content().toString()).contains("Connection pool", "runbook:recommendation");
        }
    }
    @Test void invalidServiceCannotReadArbitraryFiles() {
        var tools = new RunbookTools();
        assertThatThrownBy(() -> tools.searchDocumentation(new RunbookTools.Input("../../secrets", RunbookTools.Topic.RUNBOOK, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.searchDocumentation(new RunbookTools.Input("recommendation", RunbookTools.Topic.RUNBOOK, 99)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
