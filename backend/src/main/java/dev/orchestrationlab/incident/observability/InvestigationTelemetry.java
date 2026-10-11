package dev.orchestrationlab.incident.observability;

import java.util.UUID;
import java.util.function.Supplier;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.*;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import dev.orchestrationlab.incident.tool.application.ToolResult;

/**
 * Micrometer metrikleri ve gözlemlerini ortaklaştırır. Metrik etiketleri araç/aşama/sonuç gibi sınırlı
 * kümelerden seçilir; inceleme UUID’si yalnızca yüksek kardinaliteli gözlem alanıdır. Model konuşması
 * veya token maliyet defteri tutmaz.
 */
@Component
public class InvestigationTelemetry {
    private final MeterRegistry meters;
    private final ObservationRegistry observations;
    private final String provider, model;
    /**
     * Registry’leri bağlar; ai profiline göre sağlayıcıyı openai/simulation, yapılandırmadan model
     * etiketini belirler.
     */
    public InvestigationTelemetry(MeterRegistry meters, ObservationRegistry observations, Environment env) {
        this.meters = meters; this.observations = observations;
        provider = env.acceptsProfiles(Profiles.of("ai")) ? "openai" : "simulation";
        model = env.getProperty("AI_CHAT_MODEL", "offline-fixture");
    }
    /**
     * İşi gözlem içinde çalıştırıp sonucunu aynen döndürür. LLM çağrısında sağlayıcı/model/prompt
     * sürümünü ekler; UUID varsa metrik etiketi yerine izleme bağlamına ekler.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    public <T> T observe(String operation, String category, UUID id, Supplier<T> work) {
        var observation = Observation.createNotStarted(operation, observations).lowCardinalityKeyValue("category", category);
        if (operation.equals("incident.llm")) observation.lowCardinalityKeyValue("provider", provider)
                .lowCardinalityKeyValue("model", model).lowCardinalityKeyValue("prompt.version", "v1");
        if (id != null) observation.highCardinalityKeyValue("investigation.id", id.toString());
        return observation.observe(work);
    }
    /**
     * Tipli araç sonucundan çağrı, süre ve hata metriklerini kaydeder. TIMEOUT için ortak zaman aşımı
     * sayacını da artırır; ham kanıtı ölçümlere yazmaz.
     */
    public void tool(ToolResult result, long nanos) {
        String outcome = result.successful() ? "success" : result.failure().name().toLowerCase(java.util.Locale.ROOT);
        meters.counter("incident.tool.calls", "tool", result.source().name(), "outcome", outcome).increment();
        meters.timer("incident.tool.duration", "tool", result.source().name(), "outcome", outcome).record(Duration.ofNanos(nanos));
        if (!result.successful()) meters.counter("incident.tool.failures", "tool", result.source().name(), "reason", outcome).increment();
        if (result.failure() == ToolResult.Failure.TIMEOUT) count("incident.timeouts", "tool");
    }
    /**
     * Belirli metrik ve sınırlı aşama etiketi için sayacı bir artırır.
     */
    public void count(String metric, String stage) { meters.counter(metric, "stage", stage).increment(); }
    /**
     * İncelemenin sonlanma sonucunu sayar ve toplam süresini zamanlayıcıya kaydeder.
     */
    public void completed(String outcome, Duration duration) {
        meters.counter("incident.investigations", "outcome", outcome).increment();
        meters.timer("incident.investigation.duration", "outcome", outcome).record(duration);
    }
    /**
     * Sahip onayı bekleme süresini karar etiketiyle kaydeder; onay veya eylem yürütmez.
     */
    public void approval(Duration duration, String outcome) { meters.timer("incident.approval.wait", "decision", outcome).record(duration); }
}
