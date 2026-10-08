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

public final class SpringAiIncidentReasoningModel implements IncidentReasoningModel, IncidentClassificationModel, IncidentSynthesisModel {
    private final ChatClient client;
    private final ChatClient classificationClient;
    private final boolean nativeOutput;
    private final ChatClient synthesisClient;
    public SpringAiIncidentReasoningModel(ChatModel model) { this(model, false); }
    public SpringAiIncidentReasoningModel(ChatModel model, boolean nativeOutput) {
        this(model, nativeOutput, io.micrometer.observation.ObservationRegistry.NOOP);
    }
    public SpringAiIncidentReasoningModel(ChatModel model, boolean nativeOutput, io.micrometer.observation.ObservationRegistry observations) {
        client = ChatClient.create(model, observations);
        classificationClient = ChatClient.builder(model, observations, null, null).defaultAdvisors(StructuredOutputValidationAdvisor.builder()
                .outputType(IncidentClassification.class).maxRepeatAttempts(1).build()).build();
        this.nativeOutput = nativeOutput;
        synthesisClient = ChatClient.builder(model, observations, null, null).defaultAdvisors(StructuredOutputValidationAdvisor.builder()
                .outputType(IncidentSynthesis.class).maxRepeatAttempts(1).build()).build();
    }
    @Override public IncidentSynthesis synthesize(String question, EvidenceBundle evidence) {
        String context = evidence.items().stream().map(e -> "[" + e.id() + "] " + e.summary())
                .collect(java.util.stream.Collectors.joining("\n"));
        return synthesisClient.prompt().system(PromptCatalog.load("synthesis"))
                .user("Question:\n" + question + "\nUNTRUSTED EVIDENCE:\n" + context).call()
                .entity(IncidentSynthesis.class, spec -> { if (nativeOutput) spec.useProviderStructuredOutput(); });
    }
    @Override public IncidentClassification classify(String question) {
        if (question == null || question.isBlank() || question.length() > 4000) throw new IllegalArgumentException("Invalid question");
        return classificationClient.prompt().system(PromptCatalog.load("classification"))
                .user(question).call().entity(IncidentClassification.class, spec -> {
                    // The explicit default validation advisor already validates/retries. Do not add a second advisor.
                    if (nativeOutput) spec.useProviderStructuredOutput();
                });
    }

    @Override public String reason(String question, ToolSession session) {
        var callbacks = Arrays.stream(ToolCallbacks.from(new SpringAiTools(session)))
                .filter(callback -> session.allows(kind(callback.getToolDefinition().name()))).toArray(ToolCallback[]::new);
        var options = ToolCallingChatOptions.builder().toolCallbacks(callbacks).build();
        List<Message> conversation = List.of(new SystemMessage(PromptCatalog.load("tool-investigation")), new UserMessage(question));
        var manager = ToolCallingManager.builder().build();
        // Spring AI 2.0: explicitly opt out of automatic looping and bound model turns in Java.
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
