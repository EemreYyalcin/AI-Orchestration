package dev.orchestrationlab.incident.orchestration.application;
import java.util.UUID;
import dev.orchestrationlab.incident.investigation.application.InvestigationService.InvestigationView;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;
/**
 * Controller ve başvuru servislerini yürütme teknolojisinden ayıran küçük sözleşmedir. temporal
 * profilinde kalıcı workflow adaptörü, diğer durumda senkron orkestratör uygulanır.
 */
public interface InvestigationExecution {
    /**
     * Sahipli incelemeyi yürütür veya kalıcı workflow başlatmasını kabul ettirir; dönen görünüm henüz
     * sonlanmamış olabilir.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    InvestigationView run(UUID owner, UUID id);
    /**
     * Sahibin tipli kararını yürütme modeline aktarır ve mevcut görünümü döndürür; kalıcı yolun
     * görünümü Update teyidinin gerisinde olabilir.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param decision Tipli sahip kararı; geçerli değerler aşama/politika tarafından denetlenir.
     */
    InvestigationView decide(UUID owner, UUID id, ApprovalDecision decision);
    /**
     * Başlatma niyetinin kayıtta tutulması gerekip gerekmediğini belirtir. Varsayılan false senkron
     * yolu, Temporal uygulamasındaki true kalıcı yolu işaret eder.
     */
    default boolean durable() { return false; }
}
