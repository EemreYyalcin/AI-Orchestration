package dev.orchestrationlab.incident.workflow;
import io.temporal.workflow.*;
import java.util.UUID;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;
/**
 * Temporal workflow giriş, Update ve Query sözleşmesidir. investigate kalıcı akışı başlatır, decide
 * sahip kararını geçmişe yazar, validateDecision Update’i denetler, phase mevcut workflow aşamasını
 * okur.
 */
@WorkflowInterface
public interface InvestigationWorkflow {
    /**
     * Sahip ve inceleme UUID’siyle akışı yürütür. approvalSeconds yapılandırmadan girdiye taşınan
     * kalıcı onay süresidir; sonucu son aşama adıdır.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param approvalSeconds Workflow girdisine taşınan onay bekleme süresi, saniye olarak.
     */
    @WorkflowMethod String investigate(UUID owner, UUID id, long approvalSeconds);
    /**
     * Sahibin APPROVED/DENIED niyetini kalıcı Update olarak kabul eder; PostgreSQL görünümü Update
     * teyidinden sonra güncellenebilir.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param decision Tipli sahip kararı; geçerli değerler aşama/politika tarafından denetlenir.
     */
    @UpdateMethod ApprovalDecision decide(UUID owner, ApprovalDecision decision);
    /**
     * Update kabulünden önce sahipliği, bekleme aşamasını, karar türünü ve önceki niyetle çelişmemeyi
     * doğrular.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param decision Tipli sahip kararı; geçerli değerler aşama/politika tarafından denetlenir.
     */
    @UpdateValidatorMethod(updateName = "decide") void validateDecision(UUID owner, ApprovalDecision decision);
    /**
     * Workflow’un mevcut aşama adını salt okunur Query ile döndürür; veritabanı görünümüyle anlık
     * eşitlik garantisi yoktur.
     */
    @QueryMethod String phase();
}
