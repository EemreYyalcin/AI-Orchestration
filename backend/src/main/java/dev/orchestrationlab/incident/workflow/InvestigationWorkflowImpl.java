package dev.orchestrationlab.incident.workflow;

import io.temporal.workflow.Workflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import java.time.Duration;
import java.util.UUID;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;

/** Deterministic only. External work and database projections are exclusively Activities. */
public class InvestigationWorkflowImpl implements InvestigationWorkflow {
    private final InvestigationActivities activities;
    public InvestigationWorkflowImpl() { this(Duration.ofMinutes(3)); }
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
    @Override public String investigate(UUID owner, UUID id, long approvalSeconds) {
        this.owner = owner;
        try {
            phase = "ANALYZING"; activities.classify(owner, id);
            phase = "COLLECTING_EVIDENCE"; activities.collect(owner, id);
            phase = "REASONING";
            if (activities.analyze(owner, id)) {
                phase = "WAITING_APPROVAL";
                if (!Workflow.await(Duration.ofSeconds(approvalSeconds), () -> decision != null)) decision = ApprovalDecision.TIMED_OUT;
                activities.complete(owner, id, decision);
            }
            phase = "COMPLETED";
        } catch (ActivityFailure failed) {
            phase = "FAILED"; activities.fail(owner, id);
        }
        return phase;
    }
    @Override public ApprovalDecision decide(UUID caller, ApprovalDecision value) {
        validateDecision(caller, value);
        decision = value; return value;
    }
    @Override public void validateDecision(UUID caller, ApprovalDecision value) {
        if (!java.util.Objects.equals(owner, caller) || !phase.equals("WAITING_APPROVAL") || (decision != null && decision != value)
                || (value != ApprovalDecision.APPROVED && value != ApprovalDecision.DENIED))
            throw new IllegalArgumentException("Decision not accepted");
    }
    @Override public String phase() { return phase; }
}
