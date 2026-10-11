package dev.orchestrationlab.incident.orchestration.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import dev.orchestrationlab.incident.investigation.application.*;
import dev.orchestrationlab.incident.investigation.domain.*;
import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.context.application.ContextBuilder;

/**
 * temporal kapalıyken kullanılan senkron inceleme yürütücüsüdür. Sınıflandırma, kanıt toplama ve
 * sentezi sabit Java sırasıyla yürütür; durum yazmalarını ayrı servis transaction’larına bırakır.
 * Kalıcı onay zamanlayıcısı içermez.
 */
@Service
@org.springframework.context.annotation.Profile("!temporal")
public class IncidentOrchestrator implements InvestigationExecution {
    private final InvestigationService investigations;
    private final InvestigationStateService state;
    private final ClassificationService classifier;
    private final ContextBuilder context;
    private final EvidenceCollector collector;
    private final SynthesisService synthesis;
    /**
     * Kullanım senaryosunun bağımlılıklarını constructor üzerinden bağlar. temporal kapalıyken
     * kullanılan senkron inceleme yürütücüsüdür. Sınıflandırma, kanıt toplama ve sentezi sabit Java
     * sırasıyla yürütür; durum yazmalarını ayrı servis transaction’larına bırakır. Kalıcı onay
     * zamanlayıcısı içermez.
     */
    public IncidentOrchestrator(InvestigationService investigations, InvestigationStateService state, ClassificationService classifier,
                                ContextBuilder context, EvidenceCollector collector, SynthesisService synthesis) {
        this.investigations = investigations; this.state = state; this.classifier = classifier;
        this.context = context; this.collector = collector; this.synthesis = synthesis;
    }
    /**
     * Yalnızca CREATED incelemeyi başlatır; sınıflandırır, kanıt toplar, sentezler ve raporu saklar.
     * Onay yalnızca geçerli recommendation restart önerisinde istenir. Zorunlu kaynak hatasında kısmi
     * kanıtı korur; çalışma hatasında FAILED kaydeder ve güncel görünümü döndürür.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    public InvestigationService.InvestigationView run(UUID owner, UUID id) {
        var original = investigations.read(owner, id);
        if (original.status() != InvestigationStatus.CREATED) throw new IllegalStateException("Investigation already started");
        state.begin(owner, id);
        try {
            var classified = classifier.classify(original.question()); state.classification(owner, id, classified.classification());
            var evidence = collector.collect(owner, id, context.select(classified.route())); state.evidence(owner, id, evidence);
            var analyzed = synthesis.analyze(original.question(), evidence);
            boolean approve = !analyzed.degraded() && analyzed.synthesis().proposedAction() == IncidentSynthesis.ProposedAction.RESTART_RECOMMENDATION_SERVICE
                    && original.question().toLowerCase(java.util.Locale.ROOT).contains("recommendation");
            // Model önerisi Java politikasından geçmelidir; desteklenmeyen hedef onaya açılamaz.
            if (!approve && analyzed.synthesis().proposedAction() != IncidentSynthesis.ProposedAction.NONE)
                throw new IllegalArgumentException("Unsupported remediation target");
            state.report(owner, id, new IncidentReport(id, classified.classification(), evidence, analyzed.synthesis(),
                    classified.degraded() || evidence.degraded() || analyzed.degraded(), true), approve);
        } catch (EvidenceCollector.RequiredEvidenceUnavailable failure) {
            state.evidenceFailure(owner, id, failure.evidence()); state.fail(owner, id);
        } catch (RuntimeException failure) { state.fail(owner, id); }
        return investigations.read(owner, id);
    }
    /**
     * Sahip kararını kaydeder, ardından ayrı tamamlanma işlemini çağırır ve güncel görünümü okur;
     * gerçek restart yürütmez.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param decision Tipli sahip kararı; geçerli değerler aşama/politika tarafından denetlenir.
     */
    public InvestigationService.InvestigationView decide(UUID owner, UUID id, ApprovalDecision decision) {
        state.decision(owner, id, decision); state.finish(owner, id); return investigations.read(owner, id);
    }
}
