package dev.orchestrationlab.incident.workflow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.client.WorkflowOptions;
import java.time.Duration;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ActivityTimeoutTest {
    @Test @Timeout(15) void actualStartToCloseDeadlineExhaustsBoundedRetryAndMarksFailure() {
        try (var env = TestWorkflowEnvironment.newInstance()) {
            var worker = env.newWorker("deadline-test");
            worker.registerWorkflowImplementationFactory(InvestigationWorkflow.class, () -> new InvestigationWorkflowImpl(Duration.ofMillis(100)));
            var activity = mock(InvestigationActivities.class);
            doAnswer(call -> { Thread.sleep(500); return null; }).when(activity).collect(any(), any());
            worker.registerActivitiesImplementations(activity); env.start();
            var workflow = env.getWorkflowClient().newWorkflowStub(InvestigationWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue("deadline-test").build());
            UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
            assertThat(workflow.investigate(owner, id, 60)).isEqualTo("FAILED");
            verify(activity, times(3)).collect(owner, id); verify(activity).fail(owner, id);
        }
    }
}
