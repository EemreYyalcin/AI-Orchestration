package dev.orchestrationlab.incident.reasoning;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.reasoning.infrastructure.SpringAiIncidentReasoningModel;
import dev.orchestrationlab.incident.orchestration.application.WorkflowRoutingPolicy;
import dev.orchestrationlab.incident.tool.application.ToolKind;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassificationTest {
    private static final String VALID = """
            {"incidentType":"DATABASE","severity":"HIGH","requiredTools":["DATABASE","DOCUMENTATION"],"summary":"Connection pool exhausted"}
            """;
    @Test void validSchemaProducesTypedClassificationAndJavaRoute() {
        var model = chatModel();
        when(model.call(any(Prompt.class))).thenReturn(response(VALID));
        var result = new ClassificationService(new SpringAiIncidentReasoningModel(model), new WorkflowRoutingPolicy())
                .classify("Database connections exhausted");
        assertThat(result.classification().incidentType()).isEqualTo(IncidentType.DATABASE);
        assertThat(result.route().requiredEvidence()).containsExactly(ToolKind.DATABASE);
        assertThat(result.degraded()).isFalse();
    }
    @Test void schemaRejectsMalformedOutputAndCorrectionCanRecover() {
        var model = chatModel();
        when(model.call(any(Prompt.class))).thenReturn(response("not JSON"), response(VALID));
        assertThat(new SpringAiIncidentReasoningModel(model).classify("database issue").incidentType()).isEqualTo(IncidentType.DATABASE);
        verify(model, times(2)).call(any(Prompt.class));
    }
    @Test void unknownToolOrEnumNeverBecomesAnExecutableRoute() {
        var model = chatModel();
        when(model.call(any(Prompt.class))).thenReturn(response(VALID.replace("DATABASE", "EXECUTE_SHELL")));
        assertThatThrownBy(() -> new SpringAiIncidentReasoningModel(model).classify("issue")).isInstanceOf(RuntimeException.class);
        verify(model, atMost(2)).call(any(Prompt.class));
    }
    @Test void repeatedMalformedOutputHasBoundedConservativeFallback() {
        var model = chatModel();
        when(model.call(any(Prompt.class))).thenReturn(response("{}"));
        var result = new ClassificationService(new SpringAiIncidentReasoningModel(model), new WorkflowRoutingPolicy()).classify("issue");
        assertThat(result.degraded()).isTrue();
        assertThat(result.classification().incidentType()).isEqualTo(IncidentType.UNKNOWN);
        assertThat(result.route().tools()).containsExactlyInAnyOrder(ToolKind.LOGS, ToolKind.DOCUMENTATION);
        verify(model, atMost(4)).call(any(Prompt.class));
    }
    @Test void applicationValidationRejectsOversizedOrMissingFields() {
        assertThatThrownBy(() -> new IncidentClassification(IncidentType.DATABASE, Severity.HIGH,
                Set.of(ToolKind.DATABASE), "x".repeat(1001))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IncidentClassification(null, Severity.HIGH, Set.of(), "summary"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void routingRejectsToolOutsideSemanticWorkflowAndRetriesOnce() {
        var model = mock(IncidentClassificationModel.class);
        var invalid = new IncidentClassification(IncidentType.APPLICATION, Severity.HIGH, Set.of(ToolKind.DATABASE), "invalid route");
        when(model.classify("question")).thenReturn(invalid, IncidentClassification.fallback());
        var policy = new WorkflowRoutingPolicy();
        assertThatThrownBy(() -> policy.route(invalid)).isInstanceOf(IllegalArgumentException.class);
        var result = new ClassificationService(model, policy).classify("question");
        assertThat(result.classification().incidentType()).isEqualTo(IncidentType.UNKNOWN);
        verify(model, times(2)).classify("question");
    }
    private ChatModel chatModel() {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        return model;
    }
    private ChatResponse response(String text) { return new ChatResponse(List.of(new Generation(new AssistantMessage(text)))); }
}
