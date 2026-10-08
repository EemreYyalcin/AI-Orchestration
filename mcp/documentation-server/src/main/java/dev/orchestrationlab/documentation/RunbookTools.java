package dev.orchestrationlab.documentation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.core.io.ClassPathResource;

public final class RunbookTools {
    @Tool(description = "Search a small local read-only runbook corpus. Service is recommendation or payments; topic is a diagnostic category. No URLs or arbitrary file paths.")
    public List<String> searchDocumentation(Input input) {
        if (input == null || !java.util.Set.of("recommendation", "payments").contains(input.service() == null ? "" : input.service())
                || input.topic() == null || input.limit() < 1 || input.limit() > 10)
            throw new IllegalArgumentException("Invalid runbook query");
        // The corpus is fixed and packaged; the model cannot choose a filesystem path.
        String resource = input.service().equals("recommendation") ? "runbooks/recommendation.txt" : "runbooks/payments.txt";
        try {
            String text = new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
            return text.lines().filter(line -> !line.isBlank()).limit(input.limit())
                    .map(line -> "runbook:" + input.service() + ": " + line.substring(0, Math.min(line.length(), 900))).toList();
        } catch (IOException unavailable) { throw new IllegalStateException("Runbook corpus unavailable"); }
    }
    public enum Topic { ERRORS, CONNECTIONS, TIMEOUTS, RUNBOOK, LATENCY }
    public record Input(String service, Topic topic, int limit) { }
}
