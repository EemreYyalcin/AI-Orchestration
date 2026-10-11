package dev.orchestrationlab.incident.context.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.context ayarlarından bağlanan kanıt bütçesidir: öğe sayısı, kaynak başına karakter, toplam
 * karakter ve araç sayısı. Varsayılanlar 12/2000/8000/4’tür. Bunlar token sayımı değildir; soru,
 * prompt ve biçimlendirme ayrıca yer kaplar.
 * @param maxEvidenceItems Paket içindeki toplam kanıt öğesi sınırı; varsayılan 12.
 * @param maxCharactersPerToolResult Kaynak başına toplam özet karakter sınırı; varsayılan 2000.
 * @param maxTotalCharacters Paketin toplam özet karakter sınırı; varsayılan 8000.
 * @param maxTools Seçilen kaynak sayısı sınırı; varsayılan ve en yüksek 4.
 */
@ConfigurationProperties("app.context")
public record ContextBudget(@DefaultValue("12") int maxEvidenceItems,
                            @DefaultValue("2000") int maxCharactersPerToolResult,
                            @DefaultValue("8000") int maxTotalCharacters,
                            @DefaultValue("4") int maxTools) {
    /**
     * Öğe 1–100, kaynak karakteri 1–10000, toplam karakter 1–40000 ve araç 1–4 sınırlarını doğrular;
     * geçersiz ayarda başlangıcı durdurur.
     */
    public ContextBudget {
        if (maxEvidenceItems < 1 || maxEvidenceItems > 100 || maxCharactersPerToolResult < 1
                || maxCharactersPerToolResult > 10000 || maxTotalCharacters < 1 || maxTotalCharacters > 40000
                || maxTools < 1 || maxTools > 4) throw new IllegalArgumentException("Invalid context budget");
    }
}
