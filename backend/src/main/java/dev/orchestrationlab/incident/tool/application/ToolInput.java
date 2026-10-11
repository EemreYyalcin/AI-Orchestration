package dev.orchestrationlab.incident.tool.application;

/**
 * Önceden tanımlı teşhis girdisidir: service izinli hedef, topic teşhis kategorisi, limit en fazla
 * istenen öğedir. SQL, URL, dosya yolu veya çalıştırılabilir kod alanı bulunmaz.
 * @param service İzinli hedef servis adı: recommendation veya payments.
 * @param topic Kapalı enum kümesindeki teşhis konusu.
 * @param limit İstenen en fazla öğe sayısı; araç girdisinde 1–10.
 */
public record ToolInput(String service, Topic topic, int limit) {
    /**
     * İzinli teşhis konuları: ERRORS hata, CONNECTIONS bağlantı, TIMEOUTS süre aşımı, RUNBOOK rehber,
     * LATENCY gecikme. Serbest komut veya sorgu değildir.
     */
    public enum Topic { ERRORS, CONNECTIONS, TIMEOUTS, RUNBOOK, LATENCY }
    /**
     * Servisi recommendation/payments, konuyu null olmayan Topic, öğe limitini 1–10 aralığında
     * doğrular; sınır dışını IllegalArgumentException ile reddeder.
     */
    public void validate() {
        if (!java.util.Set.of("recommendation", "payments").contains(service == null ? "" : service)
                || topic == null || limit < 1 || limit > 10)
            throw new IllegalArgumentException("Invalid diagnostic input");
    }
}
