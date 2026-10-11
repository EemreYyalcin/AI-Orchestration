package dev.orchestrationlab.incident.investigation.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import dev.orchestrationlab.incident.orchestration.application.InvestigationExecution;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;

/**
 * HTTP controller ile kayıt/yürütme servisleri arasında kabul ve yaşam döngüsü koordinasyonu yapar.
 * Redis kontrolü ve Temporal çağrıları veritabanı transaction’ına alınmaz; kaydetme ile uzak başlatma
 * ayrı adımlardır.
 */
@Service
public class InvestigationSubmissionService {
    private final InvestigationService investigations;
    private final InvestigationExecution execution;
    private final InvestigationStartLimiter limiter;
    /**
     * Kullanım senaryosunun bağımlılıklarını constructor üzerinden bağlar. HTTP controller ile
     * kayıt/yürütme servisleri arasında kabul ve yaşam döngüsü koordinasyonu yapar. Redis kontrolü ve
     * Temporal çağrıları veritabanı transaction’ına alınmaz; kaydetme ile uzak başlatma ayrı
     * adımlardır.
     */
    public InvestigationSubmissionService(InvestigationService investigations, InvestigationExecution execution, InvestigationStartLimiter limiter) {
        this.investigations = investigations; this.execution = execution; this.limiter = limiter;
    }
    /**
     * Önce kabul limitini denetler, sonra kaydı ve gerekiyorsa kalıcı başlatma niyetini oluşturur.
     * Kalıcı yürütmede başlatmayı dener; 503 halinde kaydedilmiş kimliği pendingStart=true ile korur.
     * Senkron profilde oluşturma yalnızca CREATED bırakır.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    public Submission submit(UUID owner, String question) {
        limiter.acquire(owner);
        var created = investigations.create(owner, question, execution.durable());
        if (!execution.durable()) return new Submission(created, false);
        try { return new Submission(execution.run(owner, created.id()), false); }
        catch (ResponseStatusException failure) {
            if (failure.getStatusCode().value() != 503) throw failure;
            return new Submission(created, true);
        }
    }
    /**
     * Önce sahipliği doğrular, kabul limitinden geçer ve aynı inceleme kimliğiyle yürütmeyi başlatır
     * veya yeniden dener.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    public InvestigationService.InvestigationView retry(UUID owner, UUID id) {
        investigations.read(owner, id); limiter.acquire(owner); return execution.run(owner, id);
    }
    /**
     * Sahibin tipli onay/ret kararını etkin yürütme adaptörüne aktarır; kalıcı ve senkron yolların
     * ayrıntısını controller’dan gizler.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param decision Tipli sahip kararı; geçerli değerler aşama/politika tarafından denetlenir.
     */
    public InvestigationService.InvestigationView decide(UUID owner, UUID id, ApprovalDecision decision) { return execution.decide(owner, id, decision); }
    /**
     * Kaydedilmiş inceleme görünümü ile kalıcı başlangıcın hâlâ bekleyip beklemediğini birlikte taşır;
     * kesintide kullanıcı aynı kimlikle devam edebilir.
     * @param pendingStart Kayıt oluştuğu halde kalıcı workflow başlangıcı hâlâ bekliyorsa true.
     */
    public record Submission(InvestigationService.InvestigationView investigation, boolean pendingStart) { }
}
