package dev.orchestrationlab.incident.reasoning.infrastructure;

import dev.orchestrationlab.incident.reasoning.application.IncidentReasoningModel;
import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.tool.application.*;

/**
 * Dış model kimlik bilgisi olmadan çalışan deterministik eğitim/test simülasyonudur. Çıktıları açıkça
 * SIMULATED etiketler; gerçek teşhis doğruluğu veya canlı sağlayıcı kalitesi kanıtı değildir.
 */
public class FakeIncidentReasoningModel implements IncidentReasoningModel, IncidentClassificationModel, IncidentSynthesisModel {
    /**
     * Simüle bulgulara eldeki tüm kanıt kimliklerini ekler. Soruda recommendation varsa izinli restart
     * önerisi, aksi halde NONE üretir; uygulama öneriyi ayrıca denetler.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    @Override public IncidentSynthesis synthesize(String question, dev.orchestrationlab.incident.context.application.EvidenceBundle evidence) {
        return new IncidentSynthesis("SIMULATED investigation: bounded evidence was collected; verify findings against real systems.",
                java.util.List.of("SIMULATED: connection pressure or dependency errors require operator review"),
                java.util.List.of("Review cited evidence before any production action"),
                evidence.items().stream().map(dev.orchestrationlab.incident.context.application.Evidence::id).collect(java.util.stream.Collectors.toSet()),
                question.toLowerCase(java.util.Locale.ROOT).contains("recommendation")
                        ? IncidentSynthesis.ProposedAction.RESTART_RECOMMENDATION_SERVICE : IncidentSynthesis.ProposedAction.NONE);
    }
    /**
     * Soru içindeki sabit İngilizce anahtar kelimelerden olay türü ve gerekli kaynağı seçer; UNKNOWN
     * için LOW, diğerleri için HIGH verir. Bu mekanizma LLM çağrısı değildir.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    @Override public IncidentClassification classify(String question) {
        String text = question.toLowerCase(java.util.Locale.ROOT);
        IncidentType type = text.contains("database") || text.contains("connection") || text.contains("pool") ? IncidentType.DATABASE
                : text.contains("null pointer") || text.contains("nullpointer") ? IncidentType.APPLICATION
                : text.contains("timeout") || text.contains("dependency") ? IncidentType.DEPENDENCY
                : text.contains("latency") || text.contains("slow") ? IncidentType.PERFORMANCE : IncidentType.UNKNOWN;
        var source = type == IncidentType.DATABASE ? ToolKind.DATABASE
                : type == IncidentType.PERFORMANCE ? ToolKind.METRICS : ToolKind.LOGS;
        return new IncidentClassification(type, type == IncidentType.UNKNOWN ? Severity.LOW : Severity.HIGH,
                java.util.Set.of(source, ToolKind.DOCUMENTATION), "SIMULATED classification: " + type);
    }
    /**
     * İzinliyse LOGS, değilse DOCUMENTATION için tek örnek araç isteği yapar ve SIMULATED sonuç metni
     * üretir; araç hatasını açıkça bildirir.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    @Override public String reason(String question, ToolSession tools) {
        var input = new ToolInput("recommendation", ToolInput.Topic.ERRORS, 3);
        var kind = tools.allows(ToolKind.LOGS) ? ToolKind.LOGS : ToolKind.DOCUMENTATION;
        var result = tools.call(kind, input); // Simüle model isteği → Java araç yürütmesi → simüle sonraki tur.
        return "SIMULATED analysis: " + (result.successful() ? String.join("; ", result.evidence())
                : "Evidence unavailable: " + result.failure());
    }
}
