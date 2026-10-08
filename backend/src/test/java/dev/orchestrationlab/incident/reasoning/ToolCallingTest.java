package dev.orchestrationlab.incident.reasoning;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import dev.orchestrationlab.incident.investigation.domain.IncidentInvestigation;
import dev.orchestrationlab.incident.investigation.repository.InvestigationRepository;
import dev.orchestrationlab.incident.reasoning.infrastructure.*;
import dev.orchestrationlab.incident.tool.application.*;
import dev.orchestrationlab.incident.tool.infrastructure.StubEvidenceAdapter;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolCallingTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private final ToolInput input = new ToolInput("recommendation", ToolInput.Topic.ERRORS, 3);
    private ToolExecutionService execution;
    private InvestigationRepository investigations;
    @BeforeEach void setup() {
        investigations = mock(InvestigationRepository.class);
        when(investigations.findByIdAndOwnerId(id, owner)).thenReturn(Optional.of(new IncidentInvestigation(owner, "test")));
        execution = new ToolExecutionService(Arrays.stream(ToolKind.values()).map(kind -> (EvidenceAdapter) new StubEvidenceAdapter(kind)).toList(),
                investigations, Duration.ofMillis(200));
    }
    @AfterEach void close() { execution.close(); }
    private ToolSession session(Set<ToolKind> allowed) { return new ToolSession(execution, new ToolContext(owner, id, allowed)); }

    @Test void registersExactlyFourSchemasAndDispatchesParsedArguments() {
        var callbacks = ToolCallbacks.from(new SpringAiTools(session(Set.of(ToolKind.values()))));
        assertThat(Arrays.stream(callbacks).map(callback -> callback.getToolDefinition().name()))
                .containsExactlyInAnyOrder("searchLogs", "queryDatabase", "searchDocumentation", "inspectMetrics");
        var logs = Arrays.stream(callbacks).filter(c -> c.getToolDefinition().name().equals("searchLogs")).findFirst().orElseThrow();
        assertThat(logs.getToolDefinition().inputSchema()).contains("service", "topic", "limit", "ERRORS");
        assertThat(logs.call("{\"input\":{\"service\":\"recommendation\",\"topic\":\"ERRORS\",\"limit\":3}}"))
                .contains("SIMULATED", "LOGS");
    }
    @Test void inputValidationRejectsUnconstrainedSqlAndUnknownService() {
        var session = session(Set.of(ToolKind.DATABASE));
        assertThat(session.call(ToolKind.DATABASE, new ToolInput("DROP TABLE", ToolInput.Topic.CONNECTIONS, 1)).failure())
                .isEqualTo(ToolResult.Failure.INVALID_ARGUMENT);
        assertThat(session.call(ToolKind.DATABASE, new ToolInput("recommendation", null, 99)).failure())
                .isEqualTo(ToolResult.Failure.INVALID_ARGUMENT);
    }
    @Test void modelCannotExpandToolAllowlistOrOwnership() {
        assertThat(session(Set.of(ToolKind.LOGS)).call(ToolKind.DATABASE, input).failure()).isEqualTo(ToolResult.Failure.FORBIDDEN);
        when(investigations.findByIdAndOwnerId(id, owner)).thenReturn(Optional.empty());
        assertThat(session(Set.of(ToolKind.LOGS)).call(ToolKind.LOGS, input).failure()).isEqualTo(ToolResult.Failure.FORBIDDEN);
    }
    @Test void adapterFailureIsExplicitAndPrivateDetailsDoNotLeak() {
        var adapter = new EvidenceAdapter() {
            public ToolKind kind() { return ToolKind.LOGS; }
            public List<String> read(ToolInput input) { throw new IllegalStateException("secret backend error"); }
        };
        try (var service = new ServiceResource(new ToolExecutionService(List.of(adapter), investigations, Duration.ofMillis(100)))) {
            var result = service.value.execute(new ToolContext(owner, id, Set.of(ToolKind.LOGS)), ToolKind.LOGS, input);
            assertThat(result.failure()).isEqualTo(ToolResult.Failure.UNAVAILABLE);
            assertThat(result.evidence()).isEmpty();
        }
    }
    @Test void timeoutIsBoundedAndResultSizeIsBounded() {
        var slow = new EvidenceAdapter() {
            public ToolKind kind() { return ToolKind.LOGS; }
            public List<String> read(ToolInput input) throws Exception { Thread.sleep(1000); return List.of("late"); }
        };
        var service = new ToolExecutionService(List.of(slow), investigations, Duration.ofMillis(10));
        try {
            assertThat(service.execute(new ToolContext(owner, id, Set.of(ToolKind.LOGS)), ToolKind.LOGS, input).failure())
                    .isEqualTo(ToolResult.Failure.TIMEOUT);
        } finally { service.close(); }
        var huge = new EvidenceAdapter() {
            public ToolKind kind() { return ToolKind.LOGS; }
            public List<String> read(ToolInput input) { return Collections.nCopies(20, "x".repeat(5000)); }
        };
        service = new ToolExecutionService(List.of(huge), investigations, Duration.ofSeconds(1));
        try {
            var result = service.execute(new ToolContext(owner, id, Set.of(ToolKind.LOGS)), ToolKind.LOGS, input);
            assertThat(result.evidence()).hasSize(3).allSatisfy(value -> assertThat(value).hasSize(1000));
        } finally { service.close(); }
    }
    @Test void fakeModelSimulatesOneRequestedToolThenAnswerWithoutProvider() {
        var tools = session(Set.of(ToolKind.LOGS));
        assertThat(new FakeIncidentReasoningModel().reason("question", tools)).contains("SIMULATED", "connection");
        assertThat(tools.callCount()).isEqualTo(1);
    }
    @Test void realSpringLoopUsesFakeChatModelAndAppendsToolResultBeforeSecondTurn() {
        var model = fakeChatModel();
        var turns = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            if (turns.incrementAndGet() == 1) return toolResponse("searchLogs");
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getInstructions()).anySatisfy(message -> {
                assertThat(message).isInstanceOf(ToolResponseMessage.class);
                assertThat(((ToolResponseMessage) message).getResponses().toString()).contains("SIMULATED");
            });
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Final conclusion based on logs"))));
        });
        assertThat(new SpringAiIncidentReasoningModel(model).reason("question", session(Set.of(ToolKind.LOGS))))
                .isEqualTo("Final conclusion based on logs");
        assertThat(turns.get()).isEqualTo(2);
    }
    @Test void repeatedModelRequestsStopAtJavaBudget() {
        var model = fakeChatModel();
        when(model.call(any(Prompt.class))).thenReturn(toolResponse("searchLogs"));
        assertThatThrownBy(() -> new SpringAiIncidentReasoningModel(model).reason("question", session(Set.of(ToolKind.LOGS))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("budget");
        verify(model, times(4)).call(any(Prompt.class));
    }
    @Test void unknownToolRequestIsNeverExecuted() {
        var model = fakeChatModel();
        when(model.call(any(Prompt.class))).thenReturn(toolResponse("executeShell"));
        var tools = session(Set.of(ToolKind.LOGS));
        assertThatThrownBy(() -> new SpringAiIncidentReasoningModel(model).reason("question", tools)).isInstanceOf(RuntimeException.class);
        assertThat(tools.callCount()).isZero();
    }
    private ChatResponse toolResponse(String name) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", name,
                        "{\"input\":{\"service\":\"recommendation\",\"topic\":\"ERRORS\",\"limit\":3}}"))).build())));
    }
    private ChatModel fakeChatModel() {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        return model;
    }
    private static class ServiceResource implements AutoCloseable {
        final ToolExecutionService value;
        ServiceResource(ToolExecutionService value) { this.value = value; }
        public void close() { value.close(); }
    }
}
