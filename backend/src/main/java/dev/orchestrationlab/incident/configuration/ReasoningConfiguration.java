package dev.orchestrationlab.incident.configuration;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.model.ChatModel;
import dev.orchestrationlab.incident.reasoning.application.IncidentReasoningModel;
import dev.orchestrationlab.incident.reasoning.infrastructure.*;
import dev.orchestrationlab.incident.tool.application.*;
import dev.orchestrationlab.incident.tool.infrastructure.StubEvidenceAdapter;
import dev.orchestrationlab.incident.tool.infrastructure.McpDocumentationAdapter;

/**
 * Model ve kanıt adaptörlerini Spring profillerine göre bağlar. ai yokken deterministik çevrimdışı
 * model, mcp yokken simüle dokümantasyon kullanılır. Log, veritabanı teşhisi ve metrik adaptörleri
 * mevcut uygulamada simülasyondur.
 */
@Configuration(proxyBeanMethods = false)
public class ReasoningConfiguration {
    /**
     * ai profili kapalıyken açıkça SIMULATED sonuçlar üreten çevrimdışı modeli kaydeder; dış sağlayıcı
     * kimlik bilgisi gerekmez.
     */
    @Bean @Profile("!ai") FakeIncidentReasoningModel fakeReasoning() { return new FakeIncidentReasoningModel(); }
    /**
     * ai profilinde ChatModel’i tipli sınıflandırma ve sentez adaptörüne bağlar. nativeOutput
     * sağlayıcıya özgü yapılandırılmış çıktıyı, observations ölçüm altyapısını belirler.
     */
    @Bean @Profile("ai") SpringAiIncidentReasoningModel liveReasoning(ChatModel model,
            @Value("${app.ai.native-structured-output:false}") boolean nativeOutput, io.micrometer.observation.ObservationRegistry observations) {
        return new SpringAiIncidentReasoningModel(model, nativeOutput, observations);
    }
    /**
     * LOGS kaynağı için simüle, salt okunur teşhis adaptörünü kaydeder.
     */
    @Bean EvidenceAdapter logEvidence() { return new StubEvidenceAdapter(ToolKind.LOGS); }
    /**
     * DATABASE kaynağı için sabit bağlantı havuzu teşhisi sağlayan simülasyonu kaydeder; uygulama
     * tablolarında SQL çalıştırmaz.
     */
    @Bean EvidenceAdapter databaseEvidence() { return new StubEvidenceAdapter(ToolKind.DATABASE); }
    /**
     * mcp kapalıyken runbook yerine açıkça simüle dokümantasyon kanıtı sağlar.
     */
    @Bean @Profile("!mcp") EvidenceAdapter documentationEvidence() { return new StubEvidenceAdapter(ToolKind.DOCUMENTATION); }
    /**
     * mcp profilinde yönetici yapılandırmasındaki URL’ye bağlanan gerçek MCP adaptörünü kaydeder.
     * Kullanıcı veya model hedef URL seçemez.
     */
    @Bean @Profile("mcp") EvidenceAdapter mcpDocumentationEvidence(@Value("${app.mcp.documentation-url:http://localhost:8081}") String url,
            io.opentelemetry.api.OpenTelemetry tracing, dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        return new McpDocumentationAdapter(url, tracing, telemetry);
    }
    /**
     * METRICS kaynağı için simüle gecikme, hata ve doygunluk özeti sağlar.
     */
    @Bean EvidenceAdapter metricsEvidence() { return new StubEvidenceAdapter(ToolKind.METRICS); }
}
