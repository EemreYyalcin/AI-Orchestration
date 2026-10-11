package dev.orchestrationlab.incident.orchestration.application;

import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.context.application.*;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Modelin bulgularını eldeki kanıt kimlikleriyle doğrular. İki anlamsal denemeden sonra temkinli,
 * eylemsiz manuel inceleme sonucu kullanır; kalite düşüşünü ayrıca döndürür. Geçerli atıf, teşhisin
 * doğru olduğunu kanıtlamaz.
 */
@Service
public class SynthesisService {
    private final IncidentSynthesisModel primary;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    /**
     * Birincil sentez modeli ve isteğe bağlı telemetriyi bağlar; kısa constructor telemetrisiz
     * kullanım sağlar.
     */
    public SynthesisService(IncidentSynthesisModel primary) { this(primary, null); }
    /**
     * Birincil sentez modeli ve isteğe bağlı telemetriyi bağlar; kısa constructor telemetrisiz
     * kullanım sağlar.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public SynthesisService(IncidentSynthesisModel primary, dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        this.primary = primary; this.telemetry = telemetry;
    }
    /**
     * Soruyu ve temizlenmiş kanıt paketini sentez modeline gönderir. Birincil çıktı/hata için en fazla
     * iki deneme yapıp atıfları doğrular; sonra doğrulanmış fallback ile degraded=true döndürür.
     * Instance sürümü gözlem/sayaç ekler ve eylemsiz manuel inceleme fallback’i kurar; statik sürüm
     * verilen iki modelle aynı politikayı uygular.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    public Result analyze(String question, EvidenceBundle evidence) {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        IncidentSynthesisModel observed = (q, e) -> {
            if (calls.incrementAndGet() > 1 && telemetry != null) telemetry.count("incident.retries", "synthesis");
            return telemetry == null ? primary.synthesize(q, e) : telemetry.observe("incident.llm", "synthesis", null, () -> primary.synthesize(q, e));
        };
        return analyze(question, evidence, observed, (q, e) -> {
            if (telemetry != null) telemetry.count("incident.model.fallback", "synthesis");
            return new IncidentSynthesis(
                "DEGRADED: model unavailable. Collected evidence requires manual investigation.",
                java.util.List.of(), java.util.List.of("Review collected evidence manually"),
                e.items().stream().map(Evidence::id).collect(Collectors.toSet()), IncidentSynthesis.ProposedAction.NONE);
        });
    }
    /**
     * Soruyu ve temizlenmiş kanıt paketini sentez modeline gönderir. Birincil çıktı/hata için en fazla
     * iki deneme yapıp atıfları doğrular; sonra doğrulanmış fallback ile degraded=true döndürür.
     * Instance sürümü gözlem/sayaç ekler ve eylemsiz manuel inceleme fallback’i kurar; statik sürüm
     * verilen iki modelle aynı politikayı uygular.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    public static Result analyze(String question, EvidenceBundle evidence, IncidentSynthesisModel primary, IncidentSynthesisModel fallback) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try { return new Result(validate(primary.synthesize(question, evidence), evidence), false); }
            catch (RuntimeException ignored) { /* Sınırlı tekrar; ham sağlayıcı hatası dışarı taşınmaz. */ }
        }
        return new Result(validate(fallback.synthesize(question, evidence), evidence), true);
    }
    /**
     * Sonucun null olmadığını ve tüm atıfların paketteki kimliklerden geldiğini doğrular. Kanıt
     * mevcutsa en az bir atıf ister; desteklenmeyen kimlikleri reddeder.
     */
    private static IncidentSynthesis validate(IncidentSynthesis result, EvidenceBundle evidence) {
        Set<String> ids = evidence.items().stream().map(Evidence::id).collect(Collectors.toSet());
        if (result == null || !ids.containsAll(result.evidenceIds()) || (!ids.isEmpty() && result.evidenceIds().isEmpty()))
            throw new IllegalArgumentException("Invalid evidence citations");
        return result;
    }
    /**
     * Tipli sentezi ve fallback kullanımı nedeniyle kalite düşüşünü birlikte taşır.
     * @param degraded Eksik/kırpılmış kanıt veya yedek model sonucu varsa true.
     */
    public record Result(IncidentSynthesis synthesis, boolean degraded) { }
}
