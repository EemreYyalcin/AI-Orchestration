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

@Timeout(20)
class InvestigationWorkflowTest {
    TestWorkflowEnvironment env;
    InvestigationActivities activities;
    UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
    @BeforeEach void start() {
        env = TestWorkflowEnvironment.newInstance();
        var worker = env.newWorker("test-investigations");
        worker.registerWorkflowImplementationTypes(InvestigationWorkflowImpl.class);
        activities = mock(InvestigationActivities.class); worker.registerActivitiesImplementations(activities); env.start();
    }
    @AfterEach void close() { env.close(); }
    InvestigationWorkflow workflow() { return env.getWorkflowClient().newWorkflowStub(InvestigationWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("test-investigations").setWorkflowId("test-" + id).build()); }
    @Test void successWithoutApproval() {
        assertThat(workflow().investigate(owner, id, 60)).isEqualTo("COMPLETED");
        verify(activities).classify(owner, id); verify(activities).collect(owner, id); verify(activities).analyze(owner, id);
        verify(activities, never()).complete(any(), any(), any());
    }
    @Test void safeActivityRetriesBeforeSuccess() {
        doThrow(new IllegalStateException("temporary failure")).doNothing().when(activities).classify(owner, id);
        assertThat(workflow().investigate(owner, id, 60)).isEqualTo("COMPLETED");
        verify(activities, times(2)).classify(owner, id);
    }
    @Test void permanentToolFailureMarksFailed() {
        doThrow(ApplicationFailure.newNonRetryableFailure("required evidence unavailable", "RequiredEvidence")).when(activities).collect(owner, id);
        assertThat(workflow().investigate(owner, id, 60)).isEqualTo("FAILED"); verify(activities).fail(owner, id);
    }
    @Test void exhaustedActivityTimeoutFailsSafely() {
        doThrow(io.temporal.failure.ApplicationFailure.newFailure("simulated timeout", "Timeout"))
                .when(activities).collect(owner, id);
        assertThat(workflow().investigate(owner, id, 60)).isEqualTo("FAILED");
        verify(activities, times(3)).collect(owner, id); verify(activities).fail(owner, id);
    }
    @Test void approvalDeadlineUsesDurableTimerAndDoesNotExecuteApprovedAction() {
        when(activities.analyze(owner, id)).thenReturn(true);
        assertThat(workflow().investigate(owner, id, 30)).isEqualTo("COMPLETED");
        verify(activities).complete(owner, id, ApprovalDecision.TIMED_OUT);
    }
    @Test void approveResumesAndForeignOwnerIsRejected() { decision(ApprovalDecision.APPROVED); }
    @Test void denyResumes() { decision(ApprovalDecision.DENIED); }
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
