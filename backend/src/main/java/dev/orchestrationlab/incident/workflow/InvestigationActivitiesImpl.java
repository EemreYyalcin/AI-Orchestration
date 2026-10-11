package dev.orchestrationlab.incident.workflow;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import dev.orchestrationlab.incident.investigation.application.*;
import dev.orchestrationlab.incident.investigation.domain.*;
import dev.orchestrationlab.incident.orchestration.application.*;
import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.context.application.ContextBuilder;

/**
 * temporal profilindeki gerçek Activity uygulamasıdır. Model ve kanıt çağrılarını workflow’un dışına
 * çıkarır; kaydedilmiş aşamaları kontrol ederek tamamlanmış işleri tekrar etmez. Dış model/okuma
 * çağrıları başarısız Activity tekrarında yeniden yapılabilir.
 */
@Component("investigationActivities") @Profile("temporal")
public class InvestigationActivitiesImpl implements InvestigationActivities {
    private final InvestigationService investigations;
    private final InvestigationStateService state;
    private final ClassificationService classifier;
    private final WorkflowRoutingPolicy routes;
    private final ContextBuilder context;
    private final EvidenceCollector collector;
    private final SynthesisService synthesis;
    /**
     * Kullanım senaryosunun bağımlılıklarını constructor üzerinden bağlar. temporal profilindeki
     * gerçek Activity uygulamasıdır. Model ve kanıt çağrılarını workflow’un dışına çıkarır;
     * kaydedilmiş aşamaları kontrol ederek tamamlanmış işleri tekrar etmez. Dış model/okuma çağrıları
     * başarısız Activity tekrarında yeniden yapılabilir.
     */
    public InvestigationActivitiesImpl(InvestigationService investigations, InvestigationStateService state, ClassificationService classifier,
            WorkflowRoutingPolicy routes, ContextBuilder context, EvidenceCollector collector, SynthesisService synthesis) {
        this.investigations = investigations; this.state = state; this.classifier = classifier; this.routes = routes;
        this.context = context; this.collector = collector; this.synthesis = synthesis;
    }
    /**
     * CREATED ise ANALYZING başlatır; hâlâ ANALYZING ise tipli model sınıflandırmasını kaydeder. Daha
     * ileri aşamalarda işlem yapmaz.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Override public void classify(UUID owner, UUID id) {
        var view = investigations.read(owner, id);
        if (view.status() == InvestigationStatus.CREATED) state.begin(owner, id);
        if (investigations.read(owner, id).status() == InvestigationStatus.ANALYZING)
            state.classification(owner, id, classifier.classify(view.question()).classification());
    }
    /**
     * Yalnızca COLLECTING_EVIDENCE aşamasında route ve bağlam seçimiyle kanıt toplar. Zorunlu kaynak
     * hatasında kısmi paketi kaydeder, ardından Temporal’ın tekrar/hata politikasına exception iletir.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Override public void collect(UUID owner, UUID id) {
        var view = investigations.read(owner, id);
        if (view.status() == InvestigationStatus.COLLECTING_EVIDENCE) {
            try { state.evidence(owner, id, collector.collect(owner, id, context.select(routes.route(view.classification())))); }
            catch (EvidenceCollector.RequiredEvidenceUnavailable failure) {
                state.evidenceFailure(owner, id, failure.evidence()); throw failure;
            }
        }
    }
    /**
     * REASONING aşamasında sentezler; yalnızca degraded olmayan recommendation restart önerisini onaya
     * açar. Raporu beklemeden önce saklar ve güncel durum WAITING_APPROVAL ise true döndürür.
     * Kanıt/sınıflandırma kalite düşüşü ayrıca raporlanır; tek başına öneriyi engellemez.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Override public boolean analyze(UUID owner, UUID id) {
        var view = investigations.read(owner, id);
        if (view.status() == InvestigationStatus.REASONING) {
            var result = synthesis.analyze(view.question(), view.evidence());
            boolean approval = result.synthesis().proposedAction() == IncidentSynthesis.ProposedAction.RESTART_RECOMMENDATION_SERVICE;
            if (approval && (result.degraded() || !view.question().toLowerCase(java.util.Locale.ROOT).contains("recommendation")))
                throw new IllegalArgumentException("Unsupported remediation proposal");
            state.report(owner, id, new IncidentReport(id, view.classification(), view.evidence(), result.synthesis(),
                    result.degraded() || view.evidence().degraded() || view.classification().equals(IncidentClassification.fallback()), true), approval);
        }
        return investigations.read(owner, id).status() == InvestigationStatus.WAITING_APPROVAL;
    }
    /**
     * Kalıcı workflow kararını kaydeder ve idempotent mock tamamlanmayı çağırır; Activity tekrarında
     * aynı karar/eylem çoğalmaz.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param decision Tipli sahip kararı; geçerli değerler aşama/politika tarafından denetlenir.
     */
    @Override public void complete(UUID owner, UUID id, ApprovalDecision decision) { state.decision(owner, id, decision); state.finish(owner, id); }
    /**
     * Sahipli incelemenin hata görünümünü StateService üzerinden kalıcılaştırır.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Override public void fail(UUID owner, UUID id) { state.fail(owner, id); }
}
