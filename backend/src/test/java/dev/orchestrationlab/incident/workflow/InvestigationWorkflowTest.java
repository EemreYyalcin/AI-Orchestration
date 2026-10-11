package dev.orchestrationlab.incident.workflow;

import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.client.*;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.*;
import java.util.*;
import java.time.Duration;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Temporal test ortamında deterministik Activity sırasını, tekrarları, kalıcı onay zamanlayıcısını ve
 * sahip Update’lerini sınar. Activity’ler mock’tur; model veya veritabanı entegrasyonu bu sınıfın
 * kapsamı değildir.
 */
@Timeout(20)
class InvestigationWorkflowTest {
    TestWorkflowEnvironment env;
    InvestigationActivities activities;
    UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
    /**
     * Her test için ayrı Temporal test ortamı, worker ve mock Activity kaydı kurup başlatır.
     */
    @BeforeEach void start() {
        env = TestWorkflowEnvironment.newInstance();
        var worker = env.newWorker("test-investigations");
        worker.registerWorkflowImplementationTypes(InvestigationWorkflowImpl.class);
        activities = mock(InvestigationActivities.class); worker.registerActivitiesImplementations(activities); env.start();
    }
    /**
     * Test sonrası Temporal test ortamını ve worker kaynaklarını kapatır.
     */
    @AfterEach void close() { env.close(); }
    /**
     * Test UUID’sine bağlı workflow stub’ını test kuyruğunda oluşturur.
     */
    InvestigationWorkflow workflow() { return env.getWorkflowClient().newWorkflowStub(InvestigationWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("test-investigations").setWorkflowId("test-" + id).build()); }
    /**
     * Sınıflandır/topla/analiz sırasının COMPLETED ile bittiğini ve onay tamamlatma Activity’sinin
     * çağrılmadığını doğrular.
     */
    @Test void successWithoutApproval() {
        assertThat(workflow().investigate(owner, id, 60)).isEqualTo("COMPLETED");
        verify(activities).classify(owner, id); verify(activities).collect(owner, id); verify(activities).analyze(owner, id);
        verify(activities, never()).complete(any(), any(), any());
    }
    /**
     * İlk sınıflandırma hatası sonrası ikinci denemenin başarılı olup workflow’u tamamladığını
     * doğrular.
     */
    @Test void safeActivityRetriesBeforeSuccess() {
        doThrow(new IllegalStateException("temporary failure")).doNothing().when(activities).classify(owner, id);
        assertThat(workflow().investigate(owner, id, 60)).isEqualTo("COMPLETED");
        verify(activities, times(2)).classify(owner, id);
    }
    /**
     * Non-retryable zorunlu kanıt hatasının FAILED ve fail Activity çağrısı ürettiğini sınar.
     */
    @Test void permanentToolFailureMarksFailed() {
        doThrow(ApplicationFailure.newNonRetryableFailure("required evidence unavailable", "RequiredEvidence")).when(activities).collect(owner, id);
        assertThat(workflow().investigate(owner, id, 60)).isEqualTo("FAILED"); verify(activities).fail(owner, id);
    }
    /**
     * Simüle timeout hatasının üç Activity denemesinden sonra FAILED durumuna dönüştüğünü doğrular;
     * gerçek deadline ayrı ActivityTimeoutTest’tedir.
     */
    @Test void exhaustedActivityTimeoutFailsSafely() {
        doThrow(io.temporal.failure.ApplicationFailure.newFailure("simulated timeout", "Timeout"))
                .when(activities).collect(owner, id);
        assertThat(workflow().investigate(owner, id, 60)).isEqualTo("FAILED");
        verify(activities, times(3)).collect(owner, id); verify(activities).fail(owner, id);
    }
    /**
     * Kalıcı onay zamanlayıcısı dolunca TIMED_OUT kararıyla tamamlandığını, APPROVED eylem yoluna
     * girilmediğini doğrular.
     */
    @Test void approvalDeadlineUsesDurableTimerAndDoesNotExecuteApprovedAction() {
        when(activities.analyze(owner, id)).thenReturn(true);
        assertThat(workflow().investigate(owner, id, 30)).isEqualTo("COMPLETED");
        verify(activities).complete(owner, id, ApprovalDecision.TIMED_OUT);
    }
    /**
     * Ortak karar senaryosunu APPROVED ile çalıştırır; yabancı Update reddi ve sahip onayıyla devam
     * kontrol edilir.
     */
    @Test void approveResumesAndForeignOwnerIsRejected() { decision(ApprovalDecision.APPROVED); }
    /**
     * Ortak karar senaryosunu DENIED ile çalıştırır; ret kararı kalıcı beklemeyi bitirmelidir.
     */
    @Test void denyResumes() { decision(ApprovalDecision.DENIED); }
    /**
     * Workflow’u asenkron başlatıp WAITING_APPROVAL bekler. Yabancı sahibin Update’ini reddettirir,
     * gerçek test sahibinin kararını kabul ettirip complete Activity çağrısını denetler.
     * @param decision Tipli sahip kararı; geçerli değerler aşama/politika tarafından denetlenir.
     */
    void decision(ApprovalDecision decision) {
        when(activities.analyze(owner, id)).thenReturn(true);
        var stub = workflow(); WorkflowClient.start(stub::investigate, owner, id, 3600L);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> stub.phase().equals("WAITING_APPROVAL"));
        assertThatThrownBy(() -> stub.decide(UUID.randomUUID(), decision)).isInstanceOf(WorkflowUpdateException.class);
        assertThat(stub.decide(owner, decision)).isEqualTo(decision);
        assertThat(WorkflowStub.fromTyped(stub).getResult(String.class)).isEqualTo("COMPLETED");
        verify(activities).complete(owner, id, decision);
    }
}
