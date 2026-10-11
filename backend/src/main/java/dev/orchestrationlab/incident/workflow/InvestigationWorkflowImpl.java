package dev.orchestrationlab.incident.workflow;

import io.temporal.workflow.Workflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import java.time.Duration;
import java.util.UUID;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;

/**
 * Deterministik Temporal iş akışıdır: sınıflandır, topla, analiz et ve gerekiyorsa kalıcı sahip onayı
 * bekle. Model/ağ/veritabanı işi yalnızca Activity’lerdedir; replay için normal saat veya thread
 * beklemesi kullanılmaz.
 */
public class InvestigationWorkflowImpl implements InvestigationWorkflow {
    private final InvestigationActivities activities;
    /**
     * Deterministik Activity stub’ını kurar: varsayılan start-to-close 3 dakika, schedule-to-close 10
     * dakika, en fazla üç deneme ve 1–3 saniye aralığı. Süre alan sürüm gerçek deadline testini
     * destekler.
     */
    public InvestigationWorkflowImpl() { this(Duration.ofMinutes(3)); }
    /**
     * Deterministik Activity stub’ını kurar: varsayılan start-to-close 3 dakika, schedule-to-close 10
     * dakika, en fazla üç deneme ve 1–3 saniye aralığı. Süre alan sürüm gerçek deadline testini
     * destekler.
     */
    public InvestigationWorkflowImpl(Duration executionTimeout) {
        activities = Workflow.newActivityStub(InvestigationActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(executionTimeout)
                    .setScheduleToCloseTimeout(Duration.ofMinutes(10))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).setInitialInterval(Duration.ofSeconds(1))
                            .setMaximumInterval(Duration.ofSeconds(3)).build()).build());
    }
    private UUID owner;
    private String phase = "CREATED";
    private ApprovalDecision decision;
    /**
     * Activity’leri sırayla çağırır; onay gerekiyorsa Workflow.await ile kalıcı zamanlayıcı kurar ve
     * süre dolunca TIMED_OUT seçer. Kararla complete çağrılır; ActivityFailure durumunda FAILED
     * aşaması ve fail Activity’si kullanılır.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param approvalSeconds Workflow girdisine taşınan onay bekleme süresi, saniye olarak.
     */
    @Override public String investigate(UUID owner, UUID id, long approvalSeconds) {
        this.owner = owner;
        try {
            phase = "ANALYZING"; activities.classify(owner, id);
            phase = "COLLECTING_EVIDENCE"; activities.collect(owner, id);
            phase = "REASONING";
            if (activities.analyze(owner, id)) {
                phase = "WAITING_APPROVAL";
                // Temporal timer geçmişe kaydedilir; worker yeniden başlasa da aynı bekleme replay ile sürer.
                if (!Workflow.await(Duration.ofSeconds(approvalSeconds), () -> decision != null)) decision = ApprovalDecision.TIMED_OUT;
                activities.complete(owner, id, decision);
            }
            phase = "COMPLETED";
        } catch (ActivityFailure failed) {
            phase = "FAILED"; activities.fail(owner, id);
        }
        return phase;
    }
    /**
     * Doğrulanan sahip kararını workflow alanına yazar; bu kalıcı niyet await koşulunu açar. Eylem
     * veya veritabanı yazması bu handler’da yapılmaz.
     */
    @Override public ApprovalDecision decide(UUID caller, ApprovalDecision value) {
        validateDecision(caller, value);
        decision = value; return value;
    }
    /**
     * Sahip eşleşmesini, WAITING_APPROVAL aşamasını ve yalnızca APPROVED/DENIED değerlerini kabul
     * eder. Beklerken aynı niyet tekrarlanabilir, farklı ikinci karar reddedilir.
     */
    @Override public void validateDecision(UUID caller, ApprovalDecision value) {
        if (!java.util.Objects.equals(owner, caller) || !phase.equals("WAITING_APPROVAL") || (decision != null && decision != value)
                || (value != ApprovalDecision.APPROVED && value != ApprovalDecision.DENIED))
            throw new IllegalArgumentException("Decision not accepted");
    }
    /**
     * Replay ile korunan mevcut workflow aşamasını döndürür; harici sistem okumaz.
     */
    @Override public String phase() { return phase; }
}
