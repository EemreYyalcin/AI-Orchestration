package dev.orchestrationlab.incident.reasoning.application;

import dev.orchestrationlab.incident.tool.application.ToolSession;

/**
 * Sınırlı araç çağrılı akıl yürütme gösteriminin sağlayıcıdan bağımsız sözleşmesidir. Bu metin
 * döndüren gösterim kalıcı sınıflandırma/toplama/sentez hattından ayrıdır; ToolSession izinleri
 * Java’dan gelir.
 */
public interface IncidentReasoningModel {
    /**
     * Soruyu izinli ve çağrı sayısı sınırlı ToolSession ile inceleyip kısa metin sonucu döndürür;
     * yapılandırılmış kalıcı IncidentReport üretmez.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    String reason(String question, ToolSession tools);
}
