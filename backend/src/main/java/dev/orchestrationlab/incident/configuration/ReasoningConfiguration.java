package dev.orchestrationlab.incident.configuration;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.model.ChatModel;
import dev.orchestrationlab.incident.reasoning.application.IncidentReasoningModel;
import dev.orchestrationlab.incident.reasoning.infrastructure.*;
import dev.orchestrationlab.incident.tool.application.*;
import dev.orchestrationlab.incident.tool.infrastructure.StubEvidenceAdapter;
import dev.orchestrationlab.incident.tool.infrastructure.McpDocumentationAdapter;

@Configuration(proxyBeanMethods = false)
public class ReasoningConfiguration {
    @Bean @Profile("!ai") FakeIncidentReasoningModel fakeReasoning() { return new FakeIncidentReasoningModel(); }
    @Bean @Profile("ai") SpringAiIncidentReasoningModel liveReasoning(ChatModel model,
            @Value("${app.ai.native-structured-output:false}") boolean nativeOutput, io.micrometer.observation.ObservationRegistry observations) {
        return new SpringAiIncidentReasoningModel(model, nativeOutput, observations);
    }
    @Bean EvidenceAdapter logEvidence() { return new StubEvidenceAdapter(ToolKind.LOGS); }
    @Bean EvidenceAdapter databaseEvidence() { return new StubEvidenceAdapter(ToolKind.DATABASE); }
    @Bean @Profile("!mcp") EvidenceAdapter documentationEvidence() { return new StubEvidenceAdapter(ToolKind.DOCUMENTATION); }
    @Bean @Profile("mcp") EvidenceAdapter mcpDocumentationEvidence(@Value("${app.mcp.documentation-url:http://localhost:8081}") String url,
            io.opentelemetry.api.OpenTelemetry tracing, dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        return new McpDocumentationAdapter(url, tracing, telemetry);
    }
    @Bean EvidenceAdapter metricsEvidence() { return new StubEvidenceAdapter(ToolKind.METRICS); }
}
