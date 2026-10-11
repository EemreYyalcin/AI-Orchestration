package dev.orchestrationlab.incident.workflow;

import java.util.UUID;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import io.temporal.client.*;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import dev.orchestrationlab.incident.orchestration.application.InvestigationExecution;
import dev.orchestrationlab.incident.investigation.application.*;
import dev.orchestrationlab.incident.investigation.domain.*;

/**
 * Sahipli incelemeyi sabit kimlikli kalıcı Temporal workflow’una bağlar. Veritabanında commit edilmiş
 * başlatma niyetini dispatch eder; sınırlı zamanlanmış denemeler ve REJECT_DUPLICATE ile tekrar
 * başlatmayı güvenli kılar. HTTP çağrısı workflow tamamlanmasını beklemez.
 */
@Service @Profile("temporal")
public class TemporalInvestigationExecution implements InvestigationExecution {
    public static final String QUEUE = "incident-investigations-v1";
    private final WorkflowClient client;
    private final InvestigationService investigations;
    private final InvestigationStateService state;
    private final JdbcTemplate jdbc;
    private final Duration approvalTimeout;
    private final java.util.concurrent.ScheduledExecutorService deadlines = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("temporal-rpc-deadlines").factory());
    /**
     * Spring kapanışında Temporal RPC süre sınırlarını yöneten daemon zamanlayıcıyı kapatır.
     */
    @jakarta.annotation.PreDestroy void closeDeadlines() { deadlines.shutdownNow(); }
    /**
     * Start/Update çağrısını toplam 5 saniyelik gRPC deadline bağlamında çalıştırır; sonunda bağlamı
     * iptal eder. Checked hatayı uygulama hatasına çevirir; kalıcı workflow süresini değiştirmez.
     */
    private <T> T remote(java.util.concurrent.Callable<T> call) {
        var context = io.grpc.Context.current().withDeadlineAfter(5, java.util.concurrent.TimeUnit.SECONDS, deadlines);
        try { return context.call(call); }
        catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("Temporal request unavailable", failure); }
        finally { context.cancel(null); }
    }
    /**
     * Temporal istemcisi, sahipli kayıt/durum servisleri ve JDBC dispatcher’ını bağlar. Pozitif, en
     * fazla bir günlük onay süresini doğrular; bu süre workflow girdisine taşınır.
     */
    public TemporalInvestigationExecution(WorkflowClient client, InvestigationService investigations, InvestigationStateService state,
            JdbcTemplate jdbc, @Value("${app.workflow.approval-timeout:PT1H}") Duration approvalTimeout) {
        this.client = client; this.investigations = investigations; this.state = state; this.jdbc = jdbc;
        if (approvalTimeout.isNegative() || approvalTimeout.isZero() || approvalTimeout.compareTo(Duration.ofDays(1)) > 0)
            throw new IllegalArgumentException("Approval timeout must be within one day");
        this.approvalTimeout = approvalTimeout;
    }
    /**
     * Bu adaptörün kalıcı başlatma niyeti gerektirdiğini true ile bildirir.
     */
    @Override public boolean durable() { return true; }
    /**
     * İnceleme UUID’sinden investigation-&lt;UUID&gt; sabit workflow kimliğini üretir; yeniden
     * denemeler aynı kimliği kullanır.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    public static String workflowId(UUID id) { return "investigation-" + id; }
    /**
     * Sahipliği okur; CREATED dışındaki kaydı aynen döndürür. CREATED için başlatma niyetini kaydedip
     * dispatch dener. Uzak hata halinde aynı kimlikle yeniden denenebilen 503 verir; niyet
     * veritabanında korunur.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Override public InvestigationService.InvestigationView run(UUID owner, UUID id) {
        var view = investigations.read(owner, id);
        if (view.status() != InvestigationStatus.CREATED) return view;
        state.requestStart(owner, id);
        try { dispatch(owner, id); }
        catch (RuntimeException unavailable) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Workflow start is pending; retry using the same investigation id"); }
        return investigations.read(owner, id);
    }
    /**
     * Deneme sayısını artırır ve sabit kuyruk/kimlikle en fazla iki günlük workflow başlatır.
     * AlreadyStarted aynı niyetin kabulü sayılır; kabulden sonra startRequested temizlenir. RPC
     * çağrısı açık veritabanı transaction’ı içinde tutulmaz.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    private void dispatch(UUID owner, UUID id) {
        jdbc.update("UPDATE incident_investigations SET start_attempts = start_attempts + 1 WHERE id = ?", id);
        var stub = client.newWorkflowStub(InvestigationWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue(QUEUE)
                .setWorkflowId(workflowId(id)).setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .setWorkflowExecutionTimeout(Duration.ofDays(2)).build());
        try { remote(() -> WorkflowClient.start(stub::investigate, owner, id, approvalTimeout.toSeconds())); }
        catch (WorkflowExecutionAlreadyStarted alreadyAccepted) { /* Sabit kimlik, tekrar dispatch’te aynı workflow’un yeniden açılmasını engeller. */ }
        state.startAccepted(owner, id);
    }
    /**
     * Her yapılandırılmış aralıkta deneme sayısı 12’den az en fazla on bekleyen kaydı seçer. Başlatma
     * hatasını sonraki tur için bırakır; tükenmiş kayıtlar açık /run ile tekrar ister. Varsayılan
     * aralık 5000 ms’dir.
     */
    @Scheduled(fixedDelayString = "${app.workflow.dispatch-delay:5000}") public void dispatchPending() {
        jdbc.query("SELECT id, owner_id FROM incident_investigations WHERE start_requested = true AND start_attempts < 12 ORDER BY created_at LIMIT 10",
                (rs, row) -> new Pending(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).forEach(value -> {
            try { dispatch(value.owner(), value.id()); } catch (RuntimeException unavailable) { /* Kalıcı istek bir sonraki sınırlı dispatcher turunda yeniden denenir. */ }
        });
    }
    /**
     * Önce sahipli görünümü okur. Tamamlanmış aynı kararın tekrarında mevcut kaydı döndürür; diğer
     * çağrıda WAITING_APPROVAL/PENDING ister. Sahip Update’ini deadline ile yollar; ret 409, belirsiz
     * teyit 503 olur. Dönen görünüm henüz Activity güncellemesini yansıtmıyor olabilir.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param decision Tipli sahip kararı; geçerli değerler aşama/politika tarafından denetlenir.
     */
    @Override public InvestigationService.InvestigationView decide(UUID owner, UUID id, ApprovalDecision decision) {
        var view = investigations.read(owner, id);
        if (view.approval() == decision && view.status() == InvestigationStatus.COMPLETED) return view;
        if (view.status() != InvestigationStatus.WAITING_APPROVAL || view.approval() != ApprovalDecision.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval is not pending");
        try { remote(() -> client.newWorkflowStub(InvestigationWorkflow.class, workflowId(id)).decide(owner, decision)); }
        catch (WorkflowUpdateException rejected) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval decision rejected"); }
        catch (RuntimeException unavailable) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Decision acknowledgement unavailable; read investigation before retrying"); }
        return investigations.read(owner, id);
    }
    /**
     * Başlatma dispatcher’ının SQL sonucundaki inceleme/sahip UUID çiftidir; yalnızca iç
     * koordinasyonda kullanılır.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     */
    private record Pending(UUID id, UUID owner) { }
}
