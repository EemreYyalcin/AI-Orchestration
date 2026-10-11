package dev.orchestrationlab.incident.investigation.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.orchestrationlab.incident.investigation.repository.InvestigationRepository;
import dev.orchestrationlab.incident.investigation.domain.IncidentInvestigation;
import dev.orchestrationlab.incident.reasoning.application.IncidentClassification;
import dev.orchestrationlab.incident.context.application.EvidenceBundle;
import dev.orchestrationlab.incident.investigation.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * İncelemenin durum, kanıt, rapor, onay ve denetim kayıtlarını kısa transaction’larda değiştirir. Her
 * işlem sahipli kaydı arar; model/araç/ağ işi bu servisin dışındadır. Mock eylem defteri ve tamamlanma
 * aynı commit içinde yazılır.
 */
@Service
public class InvestigationStateService {
    private final InvestigationRepository investigations;
    private final JdbcTemplate jdbc;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    /**
     * Kullanım senaryosunun bağımlılıklarını constructor üzerinden bağlar. İncelemenin durum, kanıt,
     * rapor, onay ve denetim kayıtlarını kısa transaction’larda değiştirir. Her işlem sahipli kaydı
     * arar; model/araç/ağ işi bu servisin dışındadır. Mock eylem defteri ve tamamlanma aynı commit
     * içinde yazılır.
     */
    public InvestigationStateService(InvestigationRepository investigations, JdbcTemplate jdbc,
                                     dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        this.investigations = investigations; this.jdbc = jdbc; this.telemetry = telemetry;
    }
    /**
     * Sahipli incelemeyi CREATED durumundan ANALYZING durumuna geçirir; geçişin geçerliliğini entity
     * denetler.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Transactional public void begin(UUID owner, UUID id) { owned(owner, id).beginAnalysis(); }
    /**
     * Tipli sınıflandırmayı kaydedip kanıt toplama aşamasına geçer; kullanılan araç kümesini denetim
     * kaydına yazar.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Transactional public void classification(UUID owner, UUID id, IncidentClassification value) {
        owned(owner, id).classify(value); audit(id, "classification", value.requiredTools().toString(), "success");
    }
    /**
     * Kanıt paketini saklayıp REASONING aşamasına geçer. Kaynak adlarını ve kalite düşüşünü denetim
     * kaydına ekler.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Transactional public void evidence(UUID owner, UUID id, EvidenceBundle value) {
        owned(owner, id).collect(value); audit(id, "evidence", value.items().stream().map(e -> e.source().name()).distinct().sorted().collect(java.util.stream.Collectors.joining(",")), value.degraded() ? "degraded" : "success");
    }
    /**
     * Zorunlu kaynak eksikliğinde eldeki kısmi paketi kaydeder ve hata denetimi yazar; sonraki aşamaya
     * geçmez.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Transactional public void evidenceFailure(UUID owner, UUID id, EvidenceBundle value) {
        owned(owner, id).evidenceFailure(value); audit(id, "evidence", "", "failure");
    }
    /**
     * Sentez raporunu kaydeder; onay gerekiyorsa WAITING_APPROVAL/PENDING, gerekmiyorsa COMPLETED
     * yapar. Onaysız tamamlanmanın denetimi ve metriğini de hazırlar.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Transactional public void report(UUID owner, UUID id, IncidentReport report, boolean approval) {
        var value = owned(owner, id); value.report(report, approval); audit(id, "report", "", report.degraded() ? "degraded" : "success");
        if (!approval) completion(value, "success");
    }
    /**
     * Kalıcı başlatma niyetini kaydeder ve deneme sayısını sıfırlar; açık yeniden başlatma bu işlemi
     * kullanır.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Transactional public void requestStart(UUID owner, UUID id) { owned(owner, id).requestStart(); }
    /**
     * Temporal başlatmayı kabul ettikten sonra bekleyen başlatma bayrağını temizler.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Transactional public void startAccepted(UUID owner, UUID id) { owned(owner, id).startAccepted(); }
    /**
     * Henüz sonlanmamış incelemeyi FAILED yapıp tamamlanma denetimini yazar. Zaten COMPLETED/FAILED
     * kayıt için işlem yapmaz.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Transactional public void fail(UUID owner, UUID id) {
        var value = owned(owner, id);
        if (value.getStatus() != InvestigationStatus.COMPLETED && value.getStatus() != InvestigationStatus.FAILED) { value.fail(); completion(value, "failure"); }
    }
    /**
     * Aynı kalıcı karar tekrarında işlem yapmaz; yeni geçerli kararı kaydedip denetim oluşturur. Onay
     * bekleme süresinin metriğini commit sonrasına bırakır; eylem bu metotta yürütülmez.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param decision Tipli sahip kararı; geçerli değerler aşama/politika tarafından denetlenir.
     */
    @Transactional public void decision(UUID owner, UUID id, ApprovalDecision decision) {
        var value = owned(owner, id);
        if (value.getApproval() == decision) return; // Aynı kalıcı niyet tekrarında ikinci değişiklik yapılmaz.
        value.decide(decision);
        audit(id, "approval", "", decision.name());
        if (telemetry != null) afterCommit(() -> telemetry.approval(java.time.Duration.between(value.getApprovalRequestedAt(), java.time.Instant.now()), decision.name()));
    }
    /**
     * Kararı verilmiş WAITING_APPROVAL incelemesini tamamlar. APPROVED için yalnızca izinli restart
     * önerisini kontrol eder ve benzersiz mock eylem kaydı ekler; ON CONFLICT tekrarı güvenli kılar.
     * Ret/süre dolması eylem kaydı üretmez; gerçek servis yeniden başlatılmaz.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Transactional public void finish(UUID owner, UUID id) {
        var value = owned(owner, id);
        if (value.getStatus() == InvestigationStatus.COMPLETED) return;
        if (value.getApproval() == ApprovalDecision.PENDING || value.getStatus() != InvestigationStatus.WAITING_APPROVAL)
            throw new IllegalStateException("Decision required");
        if (value.getApproval() == ApprovalDecision.APPROVED) {
            if (value.getReport().findings().proposedAction() != dev.orchestrationlab.incident.reasoning.application.IncidentSynthesis.ProposedAction.RESTART_RECOMMENDATION_SERVICE)
                throw new IllegalStateException("Action not allowed");
            jdbc.update("INSERT INTO mock_remediation_actions (investigation_id, action) VALUES (?, 'RESTART_RECOMMENDATION_SERVICE') ON CONFLICT (investigation_id) DO NOTHING", id);
        }
        value.completeAction(); // Eylem defteri ve tamamlanma birlikte commit edilir; gerçek servis yeniden başlatılmaz.
        completion(value, "success");
    }
    /**
     * İnceleme/aşama/sonuç benzersizliğiyle v1 workflow ve prompt sürümlerini denetim tablosuna yazar.
     * Tekrarlı Activity yürütmeleri aynı denetimi çoğaltmaz.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    private void audit(UUID id, String stage, String tools, String outcome) {
        jdbc.update("INSERT INTO investigation_audit (investigation_id, stage, workflow_version, prompt_version, tools, outcome) VALUES (?, ?, 'v1', 'v1', ?, ?) ON CONFLICT DO NOTHING", id, stage, tools, outcome);
    }
    /**
     * Sonlanma denetimini yazar ve toplam inceleme süresi metriğini commit sonrasında kaydeder.
     */
    private void completion(IncidentInvestigation value, String outcome) {
        audit(value.getId(), "terminal", value.getApproval() == ApprovalDecision.APPROVED ? "RESTART_RECOMMENDATION_SERVICE" : "", outcome);
        if (telemetry != null) afterCommit(() -> telemetry.completed(outcome, java.time.Duration.between(value.getCreatedAt(), java.time.Instant.now())));
    }
    /**
     * Ölçüm gibi yan işleri başarılı transaction commit’inden sonra çalıştırır; geri alınmış
     * değişiklikler için başarı metriği üretmez.
     */
    private void afterCommit(Runnable action) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            /**
             * Ölçüm gibi yan işleri başarılı transaction commit’inden sonra çalıştırır; geri alınmış
             * değişiklikler için başarı metriği üretmez.
             */
            @Override public void afterCommit() { action.run(); }
        });
    }
    /**
     * UUID ve sahip UUID’siyle entity’yi yükler; bulunamayan veya yabancı kayıt için ortak NotFound
     * hatasını fırlatır.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    private IncidentInvestigation owned(UUID owner, UUID id) {
        return investigations.findByIdAndOwnerId(id, owner).orElseThrow(InvestigationService.NotFound::new);
    }
}
