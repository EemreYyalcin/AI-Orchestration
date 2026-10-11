package dev.orchestrationlab.incident.investigation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.orchestrationlab.incident.investigation.domain.*;
import dev.orchestrationlab.incident.investigation.repository.InvestigationRepository;
import dev.orchestrationlab.incident.user.repository.AppUserRepository;
import dev.orchestrationlab.incident.reasoning.application.IncidentClassification;
import dev.orchestrationlab.incident.context.application.EvidenceBundle;

/**
 * Sahipli inceleme oluşturma ve okuma kullanım senaryolarını kısa veritabanı transaction’larında
 * yürütür. Entity’leri API görünümüne dönüştürür; model, araç veya Temporal ağ çağrısı yapmaz.
 */
@Service
public class InvestigationService {
    private final InvestigationRepository investigations;
    private final AppUserRepository users;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    /**
     * Kullanım senaryosunun bağımlılıklarını constructor üzerinden bağlar. Sahipli inceleme oluşturma
     * ve okuma kullanım senaryolarını kısa veritabanı transaction’larında yürütür. Entity’leri API
     * görünümüne dönüştürür; model, araç veya Temporal ağ çağrısı yapmaz.
     */
    public InvestigationService(InvestigationRepository investigations, AppUserRepository users,
                                dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        this.telemetry = telemetry;
        this.investigations = investigations;
        this.users = users;
    }

    /**
     * Dahili sahibin varlığını ve entity üzerinden soru sınırlarını doğrulayarak CREATED kaydını
     * oluşturur. requestDurableStart verilirse başlatma niyetini aynı commit içinde kaydeder. Kabul
     * metriği yalnızca commit başarılı olduktan sonra artırılır; iki argümanlı sürüm kalıcı başlatma
     * istemez.
     * @param ownerId İncelemenin veya oturumun dahili kullanıcı UUID’si.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    @Transactional
    public InvestigationView create(UUID ownerId, String question) {
        return create(ownerId, question, false);
    }
    /**
     * Dahili sahibin varlığını ve entity üzerinden soru sınırlarını doğrulayarak CREATED kaydını
     * oluşturur. requestDurableStart verilirse başlatma niyetini aynı commit içinde kaydeder. Kabul
     * metriği yalnızca commit başarılı olduktan sonra artırılır; iki argümanlı sürüm kalıcı başlatma
     * istemez.
     * @param ownerId İncelemenin veya oturumun dahili kullanıcı UUID’si.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     * @param requestDurableStart Başlatma niyetinin inceleme kaydıyla aynı commit içinde tutulup tutulmayacağı.
     */
    @Transactional
    public InvestigationView create(UUID ownerId, String question, boolean requestDurableStart) {
        if (!users.existsById(ownerId)) throw new MissingOwner();
        var value = new IncidentInvestigation(ownerId, question);
        if (requestDurableStart) value.requestStart(); // Niyet kayıtla aynı commit’tedir; kaydetme/başlatma arasındaki çökme boşluğunu kapatır.
        var saved = investigations.saveAndFlush(value);
        if (telemetry != null) org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    /**
                     * Transaction başarıyla commit edildikten sonra kabul metriğini artırır; rollback
                     * olmuş kayıtları başlatılmış gibi saymaz.
                     */
                    @Override public void afterCommit() { telemetry.count("incident.investigation.starts", "admitted"); }
                });
        return view(saved);
    }

    /**
     * İncelemeyi id ve ownerId birlikte filtreleyerek okur. Kayıt yoksa veya başka kullanıcıya aitse
     * aynı NotFound hatasını verir; başka kullanıcının kaydını ifşa etmez.
     * @param ownerId İncelemenin veya oturumun dahili kullanıcı UUID’si.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Transactional(readOnly = true)
    public InvestigationView read(UUID ownerId, UUID id) {
        return view(investigations.findByIdAndOwnerId(id, ownerId).orElseThrow(NotFound::new));
    }

    /**
     * Kalıcı entity’yi dışarıya sunulan InvestigationView nesnesine dönüştürür. Henüz tamamlanmamış
     * classification/evidence/report aşamaları null kalabilir.
     */
    private static InvestigationView view(IncidentInvestigation value) {
        return new InvestigationView(value.getId(), value.getQuestion(), value.getStatus(),
                value.getCreatedAt(), value.getUpdatedAt(), value.getClassification(), value.getEvidence(), value.getReport(), value.getApproval());
    }

    /**
     * API’nin sahipli inceleme görünümüdür: kimlik/soru/durum/zamanlar ve ilerledikçe dolan
     * sınıflandırma, kanıt, rapor, onay. Başlatma deneme sayısı gibi iç alanlar sunulmaz.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    public record InvestigationView(UUID id, String question, InvestigationStatus status,
                                    Instant createdAt, Instant updatedAt, IncidentClassification classification, EvidenceBundle evidence,
                                    IncidentReport report, ApprovalDecision approval) { }
    /**
     * Kayıt yokluğu ve başka kullanıcıya ait kayıt için ortak uygulama hatasıdır; HTTP katmanı bunu
     * güvenli 404’e çevirir.
     */
    public static final class NotFound extends RuntimeException { }
    /**
     * Oturum kimliğinin artık kalıcı kullanıcıya karşılık gelmediğini bildirir; HTTP katmanı 401
     * üretir.
     */
    public static final class MissingOwner extends RuntimeException { }
}
