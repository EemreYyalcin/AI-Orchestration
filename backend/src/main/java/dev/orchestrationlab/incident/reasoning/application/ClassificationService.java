package dev.orchestrationlab.incident.reasoning.application;

import org.springframework.stereotype.Service;
import dev.orchestrationlab.incident.orchestration.application.WorkflowRoutingPolicy;

/**
 * Model sınıflandırması ile Java yönlendirme politikasını birlikte doğrular. Hatalı/erişilemeyen
 * modelde iki deneme sonrası UNKNOWN/LOW ve temkinli kaynaklarla devam eder; fallback kullanımını
 * degraded bayrağıyla bildirir.
 */
@Service
public class ClassificationService {
    private final IncidentClassificationModel model;
    private final WorkflowRoutingPolicy routes;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    /**
     * Model, sabit Java route politikası ve isteğe bağlı telemetriyi bağlar; kısa constructor
     * telemetrisiz kullanım sağlar.
     */
    public ClassificationService(IncidentClassificationModel model, WorkflowRoutingPolicy routes) { this(model, routes, null); }
    /**
     * Model, sabit Java route politikası ve isteğe bağlı telemetriyi bağlar; kısa constructor
     * telemetrisiz kullanım sağlar.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ClassificationService(IncidentClassificationModel model, WorkflowRoutingPolicy routes, dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        this.telemetry = telemetry;
        this.model = model; this.routes = routes;
    }
    /**
     * Soruyu modele gönderip tipli sonucu Java route politikasından geçirir. İlk hata sonrasında bir
     * kez tekrarlar; denemeler tükenirse IncidentClassification.fallback ve ona ait route döndürür.
     * Telemetri varsa model süreleri ve tekrar sayısı kaydedilir.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    public Result classify(String question) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (attempt > 0 && telemetry != null) telemetry.count("incident.retries", "classification");
                var value = telemetry == null ? model.classify(question) : telemetry.observe("incident.llm", "classification", null, () -> model.classify(question));
                return new Result(value, routes.route(value), false);
            } catch (RuntimeException invalidOrUnavailable) { /* Salt okunur sınırlı tekrar. */ }
        }
        var fallback = IncidentClassification.fallback();
        return new Result(fallback, routes.route(fallback), true);
    }
    /**
     * Doğrulanmış sınıflandırma, Java route ve fallback kullanıldıysa degraded=true bilgisini birlikte
     * döndürür.
     * @param degraded Eksik/kırpılmış kanıt veya yedek model sonucu varsa true.
     */
    public record Result(IncidentClassification classification, WorkflowRoutingPolicy.Route route, boolean degraded) { }
}
