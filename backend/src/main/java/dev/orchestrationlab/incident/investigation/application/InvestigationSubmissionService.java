package dev.orchestrationlab.incident.investigation.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import dev.orchestrationlab.incident.orchestration.application.InvestigationExecution;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;

/** Admission and lifecycle coordination, outside the HTTP controller and database transaction. */
@Service
public class InvestigationSubmissionService {
    private final InvestigationService investigations;
    private final InvestigationExecution execution;
    private final InvestigationStartLimiter limiter;
    public InvestigationSubmissionService(InvestigationService investigations, InvestigationExecution execution, InvestigationStartLimiter limiter) {
        this.investigations = investigations; this.execution = execution; this.limiter = limiter;
    }
    public Submission submit(UUID owner, String question) {
        limiter.acquire(owner);
        var created = investigations.create(owner, question, execution.durable());
        if (!execution.durable()) return new Submission(created, false);
        try { return new Submission(execution.run(owner, created.id()), false); }
        catch (ResponseStatusException failure) {
            if (failure.getStatusCode().value() != 503) throw failure;
            return new Submission(created, true);
        }
    }
    public InvestigationService.InvestigationView retry(UUID owner, UUID id) {
        investigations.read(owner, id); limiter.acquire(owner); return execution.run(owner, id);
    }
    public InvestigationService.InvestigationView decide(UUID owner, UUID id, ApprovalDecision decision) { return execution.decide(owner, id, decision); }
    public record Submission(InvestigationService.InvestigationView investigation, boolean pendingStart) { }
}
