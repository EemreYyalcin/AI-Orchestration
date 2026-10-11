package dev.orchestrationlab.incident.investigation.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import dev.orchestrationlab.incident.reasoning.application.IncidentClassification;
import dev.orchestrationlab.incident.context.application.EvidenceBundle;

/**
 * incident_investigations tablosuna eşlenen inceleme entity’si ve yaşam döngüsü koruyucusudur.
 * Sahip/soru değişmez; sınıflandırma, kanıt ve rapor JSONB olarak saklanır. @Version eşzamanlı yazma
 * çakışmalarını yakalar; yalnızca izinli durum geçişleri yapılabilir.
 */
@Entity
@Table(name = "incident_investigations")
public class IncidentInvestigation {
    // UUID veritabanı kaydının dahili kimliğidir; dış sağlayıcı subject’i ile ilişkili değildir.
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    // JPA optimistic lock sürümü: eşzamanlı kayıp güncellemeleri sessizce kabul etmez.
    @Version private long version;
    @Column(name = "approval_requested_at") private Instant approvalRequestedAt;
    /**
     * Onay beklemenin başladığı zamanı döndürür; onay istenmediyse null olabilir.
     */
    public Instant getApprovalRequestedAt() { return approvalRequestedAt; }
    // Başlatma niyeti workflow RPC’sinden önce commit edilir; dispatcher bu bayrağı okur.
    @Column(name = "start_requested", nullable = false) private boolean startRequested;
    // Dispatch öncesi JDBC ile artırılır; 12 deneme sonrası açık /run ile yenileme gerekir.
    @Column(name = "start_attempts", nullable = false) private int startAttempts;
    /**
     * Başlatma niyetini true yapar ve deneme sayısını sıfırlar; dispatcher başarısız başlangıçları bu
     * kalıcı alanlardan izler.
     */
    public void requestStart() { startRequested = true; startAttempts = 0; }
    /**
     * Uzak workflow kabul edildiğinde başlatma isteği bayrağını kaldırır; workflow’un tamamlandığı
     * anlamına gelmez.
     */
    public void startAccepted() { startRequested = false; }
    // JSONB aşama sonuçlarıdır; onay beklemeye geçmeden önce rapor kalıcı olarak saklanır.
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private IncidentReport report;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ApprovalDecision approval = ApprovalDecision.NONE;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private IncidentClassification classification;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private EvidenceBundle evidence;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(nullable = false, length = 4000, updatable = false) private String question;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private InvestigationStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    /**
     * JPA’nın entity’yi veritabanından oluşturabilmesi için gereken boş constructor’dır; uygulama yeni
     * kayıt için doğrulamalı constructor’ı kullanır.
     */
    protected IncidentInvestigation() { }

    /**
     * Null olmayan sahip UUID’si ve 1–4000 karakterlik boş olmayan soruyla CREATED kayıt kurar; soru
     * baş/son boşlukları temizlenir.
     */
    public IncidentInvestigation(UUID ownerId, String question) {
        this.ownerId = java.util.Objects.requireNonNull(ownerId);
        if (question == null || question.isBlank() || question.length() > 4000)
            throw new IllegalArgumentException("Question must contain 1–4000 characters");
        this.question = question.strip();
        this.status = InvestigationStatus.CREATED;
    }

    /**
     * JPA ilk ekleme öncesinde createdAt ve updatedAt alanlarını aynı Instant ile doldurur.
     */
    @PrePersist void initialize() { createdAt = updatedAt = Instant.now(); }
    /**
     * JPA güncelleme öncesinde updatedAt alanını yeniler; createdAt korunur.
     */
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    /**
     * Veritabanının oluşturduğu dahili kayıt UUID’sini döndürür.
     */
    public UUID getId() { return id; }
    /**
     * İncelemenin değişmez sahibinin dahili kullanıcı UUID’sini döndürür.
     */
    public UUID getOwnerId() { return ownerId; }
    /**
     * Kalıcılaştırılmış ve baş/son boşlukları temizlenmiş soru metnini döndürür.
     */
    public String getQuestion() { return question; }
    /**
     * İncelemenin son kaydedilmiş yaşam döngüsü aşamasını döndürür.
     */
    public InvestigationStatus getStatus() { return status; }
    /**
     * Kaydın ilk oluşturulma zamanını döndürür; güncellemelerde korunur.
     */
    public Instant getCreatedAt() { return createdAt; }
    /**
     * Kaydın JPA tarafından son güncellenme zamanını döndürür.
     */
    public Instant getUpdatedAt() { return updatedAt; }
    /**
     * Kaydedilmiş tipli sınıflandırmayı döndürür; aşama tamamlanmadıysa null olabilir.
     */
    public IncidentClassification getClassification() { return classification; }
    /**
     * Kaydedilmiş temizlenmiş kanıt paketini döndürür; henüz toplanmadıysa null olabilir.
     */
    public EvidenceBundle getEvidence() { return evidence; }
    /**
     * Onay beklenmeden önce kaydedilen raporu döndürür; sentez tamamlanmadıysa null olabilir.
     */
    public IncidentReport getReport() { return report; }
    /**
     * Onay alanının güncel kararını döndürür; genel durumdan ayrı değerlendirilir.
     */
    public ApprovalDecision getApproval() { return approval; }
    /**
     * Yalnızca REASONING sırasında raporu kabul eder. Onay gerekiyorsa PENDING ve istek zamanı
     * kaydeder; ardından WAITING_APPROVAL, aksi durumda COMPLETED durumuna geçer.
     */
    public void report(IncidentReport value, boolean needsApproval) {
        if (status != InvestigationStatus.REASONING) throw new IllegalStateException("Investigation is not reasoning");
        report = java.util.Objects.requireNonNull(value);
        approval = needsApproval ? ApprovalDecision.PENDING : ApprovalDecision.NONE;
        if (needsApproval) approvalRequestedAt = Instant.now();
        transition(needsApproval ? InvestigationStatus.WAITING_APPROVAL : InvestigationStatus.COMPLETED);
    }
    /**
     * Yalnızca WAITING_APPROVAL/PENDING kaydında APPROVED, DENIED veya TIMED_OUT değerini kabul eder.
     * Kararı saklar; genel durumu tamamlatma ayrı adımda yapılır.
     * @param decision Tipli sahip kararı; geçerli değerler aşama/politika tarafından denetlenir.
     */
    public void decide(ApprovalDecision decision) {
        if (status != InvestigationStatus.WAITING_APPROVAL || approval != ApprovalDecision.PENDING)
            throw new IllegalStateException("Approval is not pending");
        if (decision != ApprovalDecision.APPROVED && decision != ApprovalDecision.DENIED && decision != ApprovalDecision.TIMED_OUT)
            throw new IllegalArgumentException("Invalid approval decision");
        approval = decision;
    }
    /**
     * Karar sonrasındaki tamamlanma geçişini uygular; eylem kaydı/yetki kontrolü çağıran servis
     * tarafından yapılır.
     */
    public void completeAction() { transition(InvestigationStatus.COMPLETED); }
    /**
     * Sonlanmamış kaydı FAILED durumuna geçirir; terminal kaydı değiştirmeye izin vermez.
     */
    public void fail() { transition(InvestigationStatus.FAILED); }
    /**
     * CREATED kaydını ANALYZING durumuna geçirerek sınıflandırma aşamasını başlatır.
     */
    public void beginAnalysis() { transition(InvestigationStatus.ANALYZING); }
    /**
     * ANALYZING sırasında null olmayan sınıflandırmayı saklayıp COLLECTING_EVIDENCE durumuna geçer.
     */
    public void classify(IncidentClassification value) {
        if (status != InvestigationStatus.ANALYZING) throw new IllegalStateException("Investigation is not analyzing");
        classification = java.util.Objects.requireNonNull(value);
        transition(InvestigationStatus.COLLECTING_EVIDENCE);
    }
    /**
     * COLLECTING_EVIDENCE sırasında null olmayan paketi saklayıp REASONING durumuna geçer.
     */
    public void collect(EvidenceBundle value) {
        if (status != InvestigationStatus.COLLECTING_EVIDENCE) throw new IllegalStateException("Investigation is not collecting evidence");
        evidence = java.util.Objects.requireNonNull(value);
        transition(InvestigationStatus.REASONING);
    }
    /**
     * Toplama sırasında kısmi kanıtı saklar; zorunlu kaynak bulunmadığı için aşama ilerlemez.
     */
    public void evidenceFailure(EvidenceBundle value) {
        if (status != InvestigationStatus.COLLECTING_EVIDENCE) throw new IllegalStateException("Investigation is not collecting evidence");
        evidence = java.util.Objects.requireNonNull(value);
    }
    /**
     * Sabit durum grafiğine göre sonraki aşamayı doğrular. Sonlanmamış aşamalardan FAILED geçişine
     * izin verir; COMPLETED ve FAILED kayıtları bütün yeni geçişlere kapatır.
     */
    private void transition(InvestigationStatus next) {
        boolean allowed = switch (status) {
            case CREATED -> next == InvestigationStatus.ANALYZING;
            case ANALYZING -> next == InvestigationStatus.COLLECTING_EVIDENCE;
            case COLLECTING_EVIDENCE -> next == InvestigationStatus.REASONING;
            case REASONING -> next == InvestigationStatus.WAITING_APPROVAL || next == InvestigationStatus.COMPLETED;
            case WAITING_APPROVAL -> next == InvestigationStatus.COMPLETED;
            case COMPLETED, FAILED -> false;
        };
        if (!allowed && next != InvestigationStatus.FAILED) throw new IllegalStateException("Invalid investigation transition");
        if (status == InvestigationStatus.COMPLETED || status == InvestigationStatus.FAILED) throw new IllegalStateException("Terminal investigation");
        status = next;
    }
}
