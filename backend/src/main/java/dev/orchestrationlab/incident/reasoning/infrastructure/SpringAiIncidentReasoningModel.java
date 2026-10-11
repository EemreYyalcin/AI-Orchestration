package dev.orchestrationlab.incident.reasoning.infrastructure;

import java.util.*;
import org.springframework.ai.chat.client.*;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import dev.orchestrationlab.incident.reasoning.application.IncidentReasoningModel;
import dev.orchestrationlab.incident.reasoning.application.IncidentClassification;
import dev.orchestrationlab.incident.reasoning.application.IncidentClassificationModel;
import dev.orchestrationlab.incident.reasoning.application.IncidentSynthesisModel;
import dev.orchestrationlab.incident.reasoning.application.IncidentSynthesis;
import dev.orchestrationlab.incident.context.application.EvidenceBundle;
import dev.orchestrationlab.incident.tool.application.*;

/**
 * Spring AI ChatModel’i uygulamanın sınıflandırma, sentez ve ayrı araçlı akıl yürütme sözleşmelerine
 * uyarlar. Sürümlü sistem prompt’ları ve tipli record dönüşümü kullanır; Java izin ve bütçe
 * kontrollerini modelin dışına taşır. Şema düzeltme tekrarı anlamsal servis tekrarından ayrı bir
 * sınırdır.
 */
public final class SpringAiIncidentReasoningModel implements IncidentReasoningModel, IncidentClassificationModel, IncidentSynthesisModel {
    private final ChatClient client;
    private final ChatClient classificationClient;
    private final boolean nativeOutput;
    private final ChatClient synthesisClient;
    /**
     * Sınıflandırma ve sentez için bir şema düzeltme tekrarlı ChatClient’lar, araçlı gösterim için
     * ayrı istemci kurar. Kısa sürümler nativeOutput=false veya NOOP gözlem varsayılanlarına devreder.
     */
    public SpringAiIncidentReasoningModel(ChatModel model) { this(model, false); }
    /**
     * Sınıflandırma ve sentez için bir şema düzeltme tekrarlı ChatClient’lar, araçlı gösterim için
     * ayrı istemci kurar. Kısa sürümler nativeOutput=false veya NOOP gözlem varsayılanlarına devreder.
     */
    public SpringAiIncidentReasoningModel(ChatModel model, boolean nativeOutput) {
        this(model, nativeOutput, io.micrometer.observation.ObservationRegistry.NOOP);
    }
    /**
     * Sınıflandırma ve sentez için bir şema düzeltme tekrarlı ChatClient’lar, araçlı gösterim için
     * ayrı istemci kurar. Kısa sürümler nativeOutput=false veya NOOP gözlem varsayılanlarına devreder.
     */
    public SpringAiIncidentReasoningModel(ChatModel model, boolean nativeOutput, io.micrometer.observation.ObservationRegistry observations) {
        client = ChatClient.create(model, observations);
        classificationClient = ChatClient.builder(model, observations, null, null).defaultAdvisors(StructuredOutputValidationAdvisor.builder()
                .outputType(IncidentClassification.class).maxRepeatAttempts(1).build()).build();
        this.nativeOutput = nativeOutput;
        synthesisClient = ChatClient.builder(model, observations, null, null).defaultAdvisors(StructuredOutputValidationAdvisor.builder()
                .outputType(IncidentSynthesis.class).maxRepeatAttempts(1).build()).build();
    }
    /**
     * Kanıt özetlerini id etiketleri ve UNTRUSTED EVIDENCE işaretiyle sentez prompt’una ekler.
     * Yapılandırılmış cevabı IncidentSynthesis’e dönüştürür; nativeOutput açıksa sağlayıcının şema
     * modunu ister. Araç çağrısı yapmaz; atıflar serviste denetlenir.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    @Override public IncidentSynthesis synthesize(String question, EvidenceBundle evidence) {
        String context = evidence.items().stream().map(e -> "[" + e.id() + "] " + e.summary())
                .collect(java.util.stream.Collectors.joining("\n"));
        return synthesisClient.prompt().system(PromptCatalog.load("synthesis"))
                .user("Question:\n" + question + "\nUNTRUSTED EVIDENCE:\n" + context).call()
                .entity(IncidentSynthesis.class, spec -> { if (nativeOutput) spec.useProviderStructuredOutput(); });
    }
    /**
     * Soruyu 1–4000 karakter sınırında doğrular ve classification-v1 politikasıyla modele gönderir.
     * Explicit şema advisor’ı tek düzeltme tekrarını yönetir; aynı advisor ikinci kez eklenmez.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    @Override public IncidentClassification classify(String question) {
        if (question == null || question.isBlank() || question.length() > 4000) throw new IllegalArgumentException("Invalid question");
        return classificationClient.prompt().system(PromptCatalog.load("classification"))
                .user(question).call().entity(IncidentClassification.class, spec -> {
                    // Açıkça kurulan advisor doğrulama/tekrarı zaten yapar; ikinci advisor eklenmez.
                    if (nativeOutput) spec.useProviderStructuredOutput();
                });
    }

    /**
     * Yalnızca ToolSession izin listesindeki callback’leri modele sunar. Otomatik araç döngüsünü
     * kapatıp Java’da en fazla dört model turu yürütür; ara araç sonuçlarını konuşmaya ekler. Son
     * yanıt boş olamaz ve 8000 karakteri aşamaz; sekiz araç çağrısı sınırı ToolSession’da ayrıca
     * uygulanır.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    @Override public String reason(String question, ToolSession session) {
        var callbacks = Arrays.stream(ToolCallbacks.from(new SpringAiTools(session)))
                .filter(callback -> session.allows(kind(callback.getToolDefinition().name()))).toArray(ToolCallback[]::new);
        var options = ToolCallingChatOptions.builder().toolCallbacks(callbacks).build();
        List<Message> conversation = List.of(new SystemMessage(PromptCatalog.load("tool-investigation")), new UserMessage(question));
        var manager = ToolCallingManager.builder().build();
        // Spring AI 2.0 otomatik döngüsünü kapat; model tur sınırını Java’da uygula.
        for (int turn = 0; turn < 4; turn++) {
            var response = client.prompt().messages(conversation).options(options.mutate())
                    .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false)).call().chatResponse();
            if (response == null) throw new IllegalStateException("Missing model response");
            if (!response.hasToolCalls()) {
                String text = response.getResult().getOutput().getText();
                if (text == null || text.isBlank() || text.length() > 8000) throw new IllegalStateException("Invalid model answer");
                return text;
            }
            if (turn == 3) break;
            conversation = manager.executeToolCalls(new Prompt(conversation, options), response).conversationHistory();
        }
        throw new IllegalStateException("Model turn budget exceeded");
    }

    /**
     * Dört kayıtlı callback adını ToolKind değerine eşler; bilinmeyen adları reddederek rastgele
     * yetenek yürütülmesini engeller.
     */
    private static ToolKind kind(String name) {
        return switch (name) {
            case "searchLogs" -> ToolKind.LOGS;
            case "queryDatabase" -> ToolKind.DATABASE;
            case "searchDocumentation" -> ToolKind.DOCUMENTATION;
            case "inspectMetrics" -> ToolKind.METRICS;
            default -> throw new IllegalArgumentException("Unknown tool");
        };
    }
}
