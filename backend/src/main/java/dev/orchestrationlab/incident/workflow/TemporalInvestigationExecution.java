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
    @jakarta.annotation.PreDestroy void closeDeadlines() { deadlines.shutdownNow(); }
    private <T> T remote(java.util.concurrent.Callable<T> call) {
        var context = io.grpc.Context.current().withDeadlineAfter(5, java.util.concurrent.TimeUnit.SECONDS, deadlines);
        try { return context.call(call); }
        catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("Temporal request unavailable", failure); }
        finally { context.cancel(null); }
    }
    public TemporalInvestigationExecution(WorkflowClient client, InvestigationService investigations, InvestigationStateService state,
            JdbcTemplate jdbc, @Value("${app.workflow.approval-timeout:PT1H}") Duration approvalTimeout) {
        this.client = client; this.investigations = investigations; this.state = state; this.jdbc = jdbc;
        if (approvalTimeout.isNegative() || approvalTimeout.isZero() || approvalTimeout.compareTo(Duration.ofDays(1)) > 0)
            throw new IllegalArgumentException("Approval timeout must be within one day");
        this.approvalTimeout = approvalTimeout;
    }
    @Override public boolean durable() { return true; }
    public static String workflowId(UUID id) { return "investigation-" + id; }
    @Override public InvestigationService.InvestigationView run(UUID owner, UUID id) {
        var view = investigations.read(owner, id);
        if (view.status() != InvestigationStatus.CREATED) return view;
        state.requestStart(owner, id);
        try { dispatch(owner, id); }
        catch (RuntimeException unavailable) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Workflow start is pending; retry using the same investigation id"); }
        return investigations.read(owner, id);
    }
    private void dispatch(UUID owner, UUID id) {
        jdbc.update("UPDATE incident_investigations SET start_attempts = start_attempts + 1 WHERE id = ?", id);
        var stub = client.newWorkflowStub(InvestigationWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue(QUEUE)
                .setWorkflowId(workflowId(id)).setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .setWorkflowExecutionTimeout(Duration.ofDays(2)).build());
        try { remote(() -> WorkflowClient.start(stub::investigate, owner, id, approvalTimeout.toSeconds())); }
        catch (WorkflowExecutionAlreadyStarted alreadyAccepted) { /* stable ID makes dispatch replay safe */ }
        state.startAccepted(owner, id);
    }
    @Scheduled(fixedDelayString = "${app.workflow.dispatch-delay:5000}") public void dispatchPending() {
        jdbc.query("SELECT id, owner_id FROM incident_investigations WHERE start_requested = true AND start_attempts < 12 ORDER BY created_at LIMIT 10",
                (rs, row) -> new Pending(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).forEach(value -> {
            try { dispatch(value.owner(), value.id()); } catch (RuntimeException unavailable) { /* persisted request retried on next bounded tick */ }
        });
    }
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
    private record Pending(UUID id, UUID owner) { }
}
