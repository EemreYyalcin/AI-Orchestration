package dev.orchestrationlab.incident.reasoning.application;

import java.util.Set;
import dev.orchestrationlab.incident.tool.application.ToolKind;

/**
 * Modelin tipli sınıflandırma çıktısıdır: incidentType olay türü, severity önem, requiredTools
 * önerilen kaynaklar, summary kısa açıklamadır. Null/boş alanlar ve boyut sınırları constructor’da
 * reddedilir; Java route politikası araç yetkisini ayrıca doğrular.
 * @param incidentType Java yönlendirmesinin kullandığı olay türü.
 * @param severity Modelin bildirdiği önem seviyesi.
 * @param requiredTools Modelin önerdiği kaynak kümesi; Java izin kontrolü ayrıca yapılır.
 * @param summary Boyutu sözleşmeyle sınırlanan açıklama/kanıt metni.
 */
public record IncidentClassification(IncidentType incidentType, Severity severity,
                                     Set<ToolKind> requiredTools, String summary) {
    /**
     * Olay türü/önem, 1–4 araç ve en fazla 1000 karakterlik boş olmayan özeti doğrular; araç kümesini
     * değişmez kopyalar.
     */
    public IncidentClassification {
        if (incidentType == null || severity == null || requiredTools == null || requiredTools.isEmpty()
                || requiredTools.size() > 4 || summary == null || summary.isBlank() || summary.length() > 1000)
            throw new IllegalArgumentException("Invalid incident classification");
        requiredTools = Set.copyOf(requiredTools);
    }
    /**
     * Sınıflandırma kullanılamadığında UNKNOWN/LOW, LOGS ve DOCUMENTATION kaynaklarıyla temkinli
     * sonucu üretir; canlı model çıktısı gibi değerlendirilmemelidir.
     */
    public static IncidentClassification fallback() {
        return new IncidentClassification(IncidentType.UNKNOWN, Severity.LOW,
                Set.of(ToolKind.LOGS, ToolKind.DOCUMENTATION), "Classification unavailable; use conservative diagnostics");
    }
}
