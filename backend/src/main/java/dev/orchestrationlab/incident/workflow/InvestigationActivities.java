package dev.orchestrationlab.incident.workflow;
import io.temporal.activity.*;
import java.util.UUID;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;
@ActivityInterface
public interface InvestigationActivities {
    void classify(UUID owner, UUID id);
    void collect(UUID owner, UUID id);
    boolean analyze(UUID owner, UUID id);
    void complete(UUID owner, UUID id, ApprovalDecision decision);
    void fail(UUID owner, UUID id);
}
