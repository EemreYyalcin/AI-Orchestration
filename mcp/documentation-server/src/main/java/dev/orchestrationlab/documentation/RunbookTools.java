package dev.orchestrationlab.documentation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.core.io.ClassPathResource;

/**
 * Paketlenmiş recommendation/payments runbook dosyalarını sınırlı salt okunur MCP aracıyla sunar.
 * Model dosya yolu veya URL seçemez. Topic doğrulanır ancak mevcut uygulamada içerik üzerinde anlamsal
 * arama veya filtreleme yapmaz.
 */
public final class RunbookTools {
    /**
     * Servis, konu ve 1–10 limitini doğrular; sabit iki classpath dosyasından birini UTF-8 okur. İlk
     * boş olmayan satırları alır, satır başına 900 karakter sınırı ve runbook kaynak etiketi uygular.
     * Kaynak okunamazsa genel hata üretir.
     */
    @Tool(description = "Search a small local read-only runbook corpus. Service is recommendation or payments; topic is a diagnostic category. No URLs or arbitrary file paths.")
    public List<String> searchDocumentation(Input input) {
        if (input == null || !java.util.Set.of("recommendation", "payments").contains(input.service() == null ? "" : input.service())
                || input.topic() == null || input.limit() < 1 || input.limit() > 10)
            throw new IllegalArgumentException("Invalid runbook query");
        // Kaynak kümesi sabit ve paketlenmiştir; model dosya sistemi yolu seçemez.
        String resource = input.service().equals("recommendation") ? "runbooks/recommendation.txt" : "runbooks/payments.txt";
        try {
            String text = new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
            return text.lines().filter(line -> !line.isBlank()).limit(input.limit())
                    .map(line -> "runbook:" + input.service() + ": " + line.substring(0, Math.min(line.length(), 900))).toList();
        } catch (IOException unavailable) { throw new IllegalStateException("Runbook corpus unavailable"); }
    }
    /**
     * İzinli teşhis konuları: ERRORS hata, CONNECTIONS bağlantı, TIMEOUTS süre aşımı, RUNBOOK rehber,
     * LATENCY gecikme. Serbest komut veya sorgu değildir.
     */
    public enum Topic { ERRORS, CONNECTIONS, TIMEOUTS, RUNBOOK, LATENCY }
    /**
     * MCP runbook isteğinin servis, konu ve öğe limiti alanlarıdır. searchDocumentation yalnızca sabit
     * servisleri ve 1–10 limitini kabul eder.
     * @param service İzinli hedef servis adı: recommendation veya payments.
     * @param topic Kapalı enum kümesindeki teşhis konusu.
     * @param limit İstenen en fazla öğe sayısı; araç girdisinde 1–10.
     */
    public record Input(String service, Topic topic, int limit) { }
}
