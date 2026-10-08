package dev.orchestrationlab.incident.investigation.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.orchestrationlab.incident.investigation.repository.InvestigationRepository;
import dev.orchestrationlab.incident.investigation.domain.IncidentInvestigation;
import dev.orchestrationlab.incident.reasoning.application.IncidentClassification;
import dev.orchestrationlab.incident.context.application.EvidenceBundle;
import dev.orchestrationlab.incident.investigation.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Short persistence transactions; model, tool and network calls are outside this service. */
@Service
public class InvestigationStateService {
    private final InvestigationRepository investigations;
    private final JdbcTemplate jdbc;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    public InvestigationStateService(InvestigationRepository investigations, JdbcTemplate jdbc,
                                     dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        this.investigations = investigations; this.jdbc = jdbc; this.telemetry = telemetry;
    }
    @Transactional public void begin(UUID owner, UUID id) { owned(owner, id).beginAnalysis(); }
    @Transactional public void classification(UUID owner, UUID id, IncidentClassification value) {
        owned(owner, id).classify(value); audit(id, "classification", value.requiredTools().toString(), "success");
    }
    @Transactional public void evidence(UUID owner, UUID id, EvidenceBundle value) {
        owned(owner, id).collect(value); audit(id, "evidence", value.items().stream().map(e -> e.source().name()).distinct().sorted().collect(java.util.stream.Collectors.joining(",")), value.degraded() ? "degraded" : "success");
    }
    @Transactional public void evidenceFailure(UUID owner, UUID id, EvidenceBundle value) {
        owned(owner, id).evidenceFailure(value); audit(id, "evidence", "", "failure");
    }
    @Transactional public void report(UUID owner, UUID id, IncidentReport report, boolean approval) {
        var value = owned(owner, id); value.report(report, approval); audit(id, "report", "", report.degraded() ? "degraded" : "success");
        if (!approval) completion(value, "success");
    }
    @Transactional public void requestStart(UUID owner, UUID id) { owned(owner, id).requestStart(); }
    @Transactional public void startAccepted(UUID owner, UUID id) { owned(owner, id).startAccepted(); }
    @Transactional public void fail(UUID owner, UUID id) {
        var value = owned(owner, id);
        if (value.getStatus() != InvestigationStatus.COMPLETED && value.getStatus() != InvestigationStatus.FAILED) { value.fail(); completion(value, "failure"); }
    }
    @Transactional public void decision(UUID owner, UUID id, ApprovalDecision decision) {
        var value = owned(owner, id);
        if (value.getApproval() == decision) return; // replay of the same durable intent
        value.decide(decision);
        audit(id, "approval", "", decision.name());
        if (telemetry != null) afterCommit(() -> telemetry.approval(java.time.Duration.between(value.getApprovalRequestedAt(), java.time.Instant.now()), decision.name()));
    }
    @Transactional public void finish(UUID owner, UUID id) {
        var value = owned(owner, id);
        if (value.getStatus() == InvestigationStatus.COMPLETED) return;
        if (value.getApproval() == ApprovalDecision.PENDING || value.getStatus() != InvestigationStatus.WAITING_APPROVAL)
            throw new IllegalStateException("Decision required");
        if (value.getApproval() == ApprovalDecision.APPROVED) {
            if (value.getReport().findings().proposedAction() != dev.orchestrationlab.incident.reasoning.application.IncidentSynthesis.ProposedAction.RESTART_RECOMMENDATION_SERVICE)
                throw new IllegalStateException("Action not allowed");
            jdbc.update("INSERT INTO mock_remediation_actions (investigation_id, action) VALUES (?, 'RESTART_RECOMMENDATION_SERVICE') ON CONFLICT (investigation_id) DO NOTHING", id);
        }
        value.completeAction(); // ledger and completion commit together; no real service is restarted
        completion(value, "success");
    }
    private void audit(UUID id, String stage, String tools, String outcome) {
        jdbc.update("INSERT INTO investigation_audit (investigation_id, stage, workflow_version, prompt_version, tools, outcome) VALUES (?, ?, 'v1', 'v1', ?, ?) ON CONFLICT DO NOTHING", id, stage, tools, outcome);
    }
    private void completion(IncidentInvestigation value, String outcome) {
        audit(value.getId(), "terminal", value.getApproval() == ApprovalDecision.APPROVED ? "RESTART_RECOMMENDATION_SERVICE" : "", outcome);
        if (telemetry != null) afterCommit(() -> telemetry.completed(outcome, java.time.Duration.between(value.getCreatedAt(), java.time.Instant.now())));
    }
    private void afterCommit(Runnable action) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
    private IncidentInvestigation owned(UUID owner, UUID id) {
        return investigations.findByIdAndOwnerId(id, owner).orElseThrow(InvestigationService.NotFound::new);
    }
}
