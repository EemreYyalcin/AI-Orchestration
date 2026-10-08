package dev.orchestrationlab.incident.workflow;
import io.temporal.workflow.*;
import java.util.UUID;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;
@WorkflowInterface
public interface InvestigationWorkflow {
    @WorkflowMethod String investigate(UUID owner, UUID id, long approvalSeconds);
    @UpdateMethod ApprovalDecision decide(UUID owner, ApprovalDecision decision);
    @UpdateValidatorMethod(updateName = "decide") void validateDecision(UUID owner, ApprovalDecision decision);
    @QueryMethod String phase();
}
