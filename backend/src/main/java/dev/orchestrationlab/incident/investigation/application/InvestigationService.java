package dev.orchestrationlab.incident.investigation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.orchestrationlab.incident.investigation.domain.*;
import dev.orchestrationlab.incident.investigation.repository.InvestigationRepository;
import dev.orchestrationlab.incident.user.repository.AppUserRepository;
import dev.orchestrationlab.incident.reasoning.application.IncidentClassification;
import dev.orchestrationlab.incident.context.application.EvidenceBundle;

@Service
public class InvestigationService {
    private final InvestigationRepository investigations;
    private final AppUserRepository users;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    public InvestigationService(InvestigationRepository investigations, AppUserRepository users,
                                dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        this.telemetry = telemetry;
        this.investigations = investigations;
        this.users = users;
    }

    @Transactional
    public InvestigationView create(UUID ownerId, String question) {
        return create(ownerId, question, false);
    }
    @Transactional
    public InvestigationView create(UUID ownerId, String question, boolean requestDurableStart) {
        if (!users.existsById(ownerId)) throw new MissingOwner();
        var value = new IncidentInvestigation(ownerId, question);
        if (requestDurableStart) value.requestStart(); // same commit as the investigation, closing the save/start crash window
        var saved = investigations.saveAndFlush(value);
        if (telemetry != null) org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { telemetry.count("incident.investigation.starts", "admitted"); }
                });
        return view(saved);
    }

    @Transactional(readOnly = true)
    public InvestigationView read(UUID ownerId, UUID id) {
        return view(investigations.findByIdAndOwnerId(id, ownerId).orElseThrow(NotFound::new));
    }

    private static InvestigationView view(IncidentInvestigation value) {
        return new InvestigationView(value.getId(), value.getQuestion(), value.getStatus(),
                value.getCreatedAt(), value.getUpdatedAt(), value.getClassification(), value.getEvidence(), value.getReport(), value.getApproval());
    }

    public record InvestigationView(UUID id, String question, InvestigationStatus status,
                                    Instant createdAt, Instant updatedAt, IncidentClassification classification, EvidenceBundle evidence,
                                    IncidentReport report, ApprovalDecision approval) { }
    public static final class NotFound extends RuntimeException { }
    public static final class MissingOwner extends RuntimeException { }
}
