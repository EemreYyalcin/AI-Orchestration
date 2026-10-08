package dev.orchestrationlab.incident.workflow;

import org.junit.jupiter.api.Test;
import io.temporal.serviceclient.*;
import io.temporal.client.*;
import io.temporal.worker.*;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit optional smoke: -Dtest=TemporalRestartSmoke; requires local Temporal. */
class TemporalRestartSmoke {
    @Test void actualServiceReplaysPendingApprovalAfterWorkerFactoryRestart() throws Exception {
        var service = WorkflowServiceStubs.newLocalServiceStubs();
        var client = WorkflowClient.newInstance(service);
        var first = WorkerFactory.newInstance(client);
        var second = WorkerFactory.newInstance(client);
        var activity = mock(InvestigationActivities.class);
        UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        when(activity.analyze(owner, id)).thenReturn(true);
        String queue = "restart-smoke-" + id;
        var worker1 = first.newWorker(queue); worker1.registerWorkflowImplementationTypes(InvestigationWorkflowImpl.class); worker1.registerActivitiesImplementations(activity);
        try {
            first.start();
            var workflow = client.newWorkflowStub(InvestigationWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue(queue)
                    .setWorkflowId("restart-smoke-" + id).setWorkflowExecutionTimeout(Duration.ofMinutes(2)).build());
            WorkflowClient.start(workflow::investigate, owner, id, 60L);
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> workflow.phase().equals("WAITING_APPROVAL"));
            first.shutdown(); first.awaitTermination(15, TimeUnit.SECONDS);
            var worker2 = second.newWorker(queue); worker2.registerWorkflowImplementationTypes(InvestigationWorkflowImpl.class); worker2.registerActivitiesImplementations(activity);
            second.start();
            assertThat(workflow.phase()).isEqualTo("WAITING_APPROVAL");
            workflow.decide(owner, ApprovalDecision.APPROVED);
            assertThat(WorkflowStub.fromTyped(workflow).getResult(15, TimeUnit.SECONDS, String.class)).isEqualTo("COMPLETED");
            verify(activity, times(1)).classify(owner, id); verify(activity, times(1)).collect(owner, id);
            verify(activity).complete(owner, id, ApprovalDecision.APPROVED);
        } finally {
            first.shutdownNow(); second.shutdownNow(); service.shutdownNow();
        }
    }
}
