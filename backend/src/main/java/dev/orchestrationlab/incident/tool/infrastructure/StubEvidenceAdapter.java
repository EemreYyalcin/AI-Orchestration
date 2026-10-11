package dev.orchestrationlab.incident.tool.infrastructure;

import java.util.List;
import dev.orchestrationlab.incident.tool.application.*;

/**
 * LOGS/DATABASE/METRICS ve mcp kapalıyken DOCUMENTATION için sabit salt okunur eğitim verisi sağlar.
 * SIMULATED etiketi gerçek operasyonel sisteme bağlanılmadığını açıkça gösterir; uygulama
 * PostgreSQL’ini teşhis için sorgulamaz.
 */
public record StubEvidenceAdapter(ToolKind kind) implements EvidenceAdapter {
    /**
     * Kaynak türüne göre sabit log, havuz, runbook veya metrik cümlesini servis adıyla döndürür. Dış
     * ağa/veritabanına erişim yapılmaz; yetki ve girdi denetimi üst katmandadır.
     */
    @Override public List<String> read(ToolInput input) {
        return List.of("SIMULATED " + input.service() + ": " + switch (kind) {
            case LOGS -> "connection acquisition timed out; HTTP dependency timeout recorded";
            case DATABASE -> "read-only connection pool snapshot: active=20, max=20, waiting=8";
            case DOCUMENTATION -> "runbook: verify pool pressure and upstream health before proposing a restart";
            case METRICS -> "p95 latency elevated; error rate=12%; saturation=100%";
        });
    }
}
