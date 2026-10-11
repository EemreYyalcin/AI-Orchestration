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

/**
 * Dört gerçek Spring AI callback şeması ve Java araç yürütme politikasını sınar. Adaptörler/ChatModel
 * kontrollü test doubles’dır; framework callback döngüsü gerçektir. Sahiplik, izin, girdi, süre, çıktı
 * ve tur sınırları denetlenir.
 */
class ToolCallingTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private final ToolInput input = new ToolInput("recommendation", ToolInput.Topic.ERRORS, 3);
    private ToolExecutionService execution;
    private InvestigationRepository investigations;
    /**
     * Her test için sahiplik döndüren repository mock’u ve dört simüle adaptörlü sınırlı yürütme
     * servisi hazırlar.
     */
    @BeforeEach void setup() {
        investigations = mock(InvestigationRepository.class);
        when(investigations.findByIdAndOwnerId(id, owner)).thenReturn(Optional.of(new IncidentInvestigation(owner, "test")));
        execution = new ToolExecutionService(Arrays.stream(ToolKind.values()).map(kind -> (EvidenceAdapter) new StubEvidenceAdapter(kind)).toList(),
                investigations, Duration.ofMillis(200));
    }
    /**
     * Testin araç havuzunu kapatır; iç ServiceResource uygulamasında sarılan servisi kapatır.
     */
    @AfterEach void close() { execution.close(); }
    /**
     * Verilen izin kümesini mevcut test sahibine/incelemesine bağlayan yeni araç oturumu oluşturur.
     */
    private ToolSession session(Set<ToolKind> allowed) { return new ToolSession(execution, new ToolContext(owner, id, allowed)); }

    /**
     * Callback adları ve JSON şema alanlarının tam dört izinli yetenekle eşleştiğini; JSON argümanının
     * tipli LOGS çağrısına dönüştüğünü doğrular.
     */
    @Test void registersExactlyFourSchemasAndDispatchesParsedArguments() {
        var callbacks = ToolCallbacks.from(new SpringAiTools(session(Set.of(ToolKind.values()))));
        assertThat(Arrays.stream(callbacks).map(callback -> callback.getToolDefinition().name()))
                .containsExactlyInAnyOrder("searchLogs", "queryDatabase", "searchDocumentation", "inspectMetrics");
        var logs = Arrays.stream(callbacks).filter(c -> c.getToolDefinition().name().equals("searchLogs")).findFirst().orElseThrow();
        assertThat(logs.getToolDefinition().inputSchema()).contains("service", "topic", "limit", "ERRORS");
        assertThat(logs.call("{\"input\":{\"service\":\"recommendation\",\"topic\":\"ERRORS\",\"limit\":3}}"))
                .contains("SIMULATED", "LOGS");
    }
    /**
     * SQL gibi servis metni, null konu ve sınır dışı limitin INVALID_ARGUMENT sonucu verdiğini
     * doğrular.
     */
    @Test void inputValidationRejectsUnconstrainedSqlAndUnknownService() {
        var session = session(Set.of(ToolKind.DATABASE));
        assertThat(session.call(ToolKind.DATABASE, new ToolInput("DROP TABLE", ToolInput.Topic.CONNECTIONS, 1)).failure())
                .isEqualTo(ToolResult.Failure.INVALID_ARGUMENT);
        assertThat(session.call(ToolKind.DATABASE, new ToolInput("recommendation", null, 99)).failure())
                .isEqualTo(ToolResult.Failure.INVALID_ARGUMENT);
    }
    /**
     * İzin dışı araç ve sahiplik eşleşmesi olmayan incelemenin FORBIDDEN ile reddedildiğini doğrular.
     */
    @Test void modelCannotExpandToolAllowlistOrOwnership() {
        assertThat(session(Set.of(ToolKind.LOGS)).call(ToolKind.DATABASE, input).failure()).isEqualTo(ToolResult.Failure.FORBIDDEN);
        when(investigations.findByIdAndOwnerId(id, owner)).thenReturn(Optional.empty());
        assertThat(session(Set.of(ToolKind.LOGS)).call(ToolKind.LOGS, input).failure()).isEqualTo(ToolResult.Failure.FORBIDDEN);
    }
    /**
     * Sır içeren adaptör exception’ının dışarı taşınmadan UNAVAILABLE/boş kanıt sonucuna çevrildiğini
     * sınar.
     */
    @Test void adapterFailureIsExplicitAndPrivateDetailsDoNotLeak() {
        var adapter = new EvidenceAdapter() {
            /**
             * Bu test adaptörünün LOGS kaynağını temsil ettiğini bildirir.
             */
            public ToolKind kind() { return ToolKind.LOGS; }
            /**
             * Test senaryosuna göre hata, gecikme veya büyük çıktı üretir; production adaptörü
             * değildir ve yalnızca yürütme sınırlarını sınamak için kullanılır.
             */
            public List<String> read(ToolInput input) { throw new IllegalStateException("secret backend error"); }
        };
        try (var service = new ServiceResource(new ToolExecutionService(List.of(adapter), investigations, Duration.ofMillis(100)))) {
            var result = service.value.execute(new ToolContext(owner, id, Set.of(ToolKind.LOGS)), ToolKind.LOGS, input);
            assertThat(result.failure()).isEqualTo(ToolResult.Failure.UNAVAILABLE);
            assertThat(result.evidence()).isEmpty();
        }
    }
    /**
     * Yavaş adaptörü TIMEOUT’a zorlar; çok uzun/çok sayıda çıktı üreten adaptörde öğe sayısı ve 1000
     * karakter sınırını denetler.
     */
    @Test void timeoutIsBoundedAndResultSizeIsBounded() {
        var slow = new EvidenceAdapter() {
            /**
             * Bu test adaptörünün LOGS kaynağını temsil ettiğini bildirir.
             */
            public ToolKind kind() { return ToolKind.LOGS; }
            /**
             * Test senaryosuna göre hata, gecikme veya büyük çıktı üretir; production adaptörü
             * değildir ve yalnızca yürütme sınırlarını sınamak için kullanılır.
             */
            public List<String> read(ToolInput input) throws Exception { Thread.sleep(1000); return List.of("late"); }
        };
        var service = new ToolExecutionService(List.of(slow), investigations, Duration.ofMillis(10));
        try {
            assertThat(service.execute(new ToolContext(owner, id, Set.of(ToolKind.LOGS)), ToolKind.LOGS, input).failure())
                    .isEqualTo(ToolResult.Failure.TIMEOUT);
        } finally { service.close(); }
        var huge = new EvidenceAdapter() {
            /**
             * Bu test adaptörünün LOGS kaynağını temsil ettiğini bildirir.
             */
            public ToolKind kind() { return ToolKind.LOGS; }
            /**
             * Test senaryosuna göre hata, gecikme veya büyük çıktı üretir; production adaptörü
             * değildir ve yalnızca yürütme sınırlarını sınamak için kullanılır.
             */
            public List<String> read(ToolInput input) { return Collections.nCopies(20, "x".repeat(5000)); }
        };
        service = new ToolExecutionService(List.of(huge), investigations, Duration.ofSeconds(1));
        try {
            var result = service.execute(new ToolContext(owner, id, Set.of(ToolKind.LOGS)), ToolKind.LOGS, input);
            assertThat(result.evidence()).hasSize(3).allSatisfy(value -> assertThat(value).hasSize(1000));
        } finally { service.close(); }
    }
    /**
     * Çevrimdışı modelin tek Java araç çağrısı yapıp SIMULATED cevap verdiğini kontrol eder.
     */
    @Test void fakeModelSimulatesOneRequestedToolThenAnswerWithoutProvider() {
        var tools = session(Set.of(ToolKind.LOGS));
        assertThat(new FakeIncidentReasoningModel().reason("question", tools)).contains("SIMULATED", "connection");
        assertThat(tools.callCount()).isEqualTo(1);
    }
    /**
     * İlk mock model cevabındaki araç çağrısının gerçek Spring AI döngüsünde çalıştığını ve ikinci
     * model turuna ToolResponseMessage eklendiğini doğrular.
     */
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
    /**
     * Sürekli araç isteyen modelin en fazla dört turdan sonra bütçe hatasıyla durdurulduğunu doğrular.
     */
    @Test void repeatedModelRequestsStopAtJavaBudget() {
        var model = fakeChatModel();
        when(model.call(any(Prompt.class))).thenReturn(toolResponse("searchLogs"));
        assertThatThrownBy(() -> new SpringAiIncidentReasoningModel(model).reason("question", session(Set.of(ToolKind.LOGS))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("budget");
        verify(model, times(4)).call(any(Prompt.class));
    }
    /**
     * executeShell gibi kayıtsız callback isteğinin reddedildiğini ve ToolSession çağrı sayısının
     * artmadığını doğrular.
     */
    @Test void unknownToolRequestIsNeverExecuted() {
        var model = fakeChatModel();
        when(model.call(any(Prompt.class))).thenReturn(toolResponse("executeShell"));
        var tools = session(Set.of(ToolKind.LOGS));
        assertThatThrownBy(() -> new SpringAiIncidentReasoningModel(model).reason("question", tools)).isInstanceOf(RuntimeException.class);
        assertThat(tools.callCount()).isZero();
    }
    /**
     * İstenen callback adına örnek tipli JSON girdisi taşıyan AssistantMessage.ToolCall oluşturur.
     */
    private ChatResponse toolResponse(String name) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", name,
                        "{\"input\":{\"service\":\"recommendation\",\"topic\":\"ERRORS\",\"limit\":3}}"))).build())));
    }
    /**
     * ToolCallingChatOptions döndüren mock ChatModel hazırlar; canlı sağlayıcıya bağlanılmaz.
     */
    private ChatModel fakeChatModel() {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        return model;
    }
    /**
     * Test araç servisini try-with-resources sonunda kapatan küçük kaynak sarmalayıcısıdır.
     */
    private static class ServiceResource implements AutoCloseable {
        final ToolExecutionService value;
        /**
         * Test servisinin referansını saklar; close çağrısı havuz temizliğini sağlar.
         */
        ServiceResource(ToolExecutionService value) { this.value = value; }
        /**
         * Sarmalanan test araç servisinin thread havuzunu kapatır.
         */
        public void close() { value.close(); }
    }
}
