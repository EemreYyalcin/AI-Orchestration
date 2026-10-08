package dev.orchestrationlab.incident.orchestration.application;
import java.util.UUID;
import dev.orchestrationlab.incident.investigation.application.InvestigationService.InvestigationView;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;
public interface InvestigationExecution {
    InvestigationView run(UUID owner, UUID id);
    InvestigationView decide(UUID owner, UUID id, ApprovalDecision decision);
    default boolean durable() { return false; }
}
