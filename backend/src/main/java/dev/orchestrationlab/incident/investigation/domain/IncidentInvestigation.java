package dev.orchestrationlab.incident.investigation.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import dev.orchestrationlab.incident.reasoning.application.IncidentClassification;
import dev.orchestrationlab.incident.context.application.EvidenceBundle;

@Entity
@Table(name = "incident_investigations")
public class IncidentInvestigation {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Version private long version;
    @Column(name = "approval_requested_at") private Instant approvalRequestedAt;
    public Instant getApprovalRequestedAt() { return approvalRequestedAt; }
    @Column(name = "start_requested", nullable = false) private boolean startRequested;
    @Column(name = "start_attempts", nullable = false) private int startAttempts;
    public void requestStart() { startRequested = true; startAttempts = 0; }
    public void startAccepted() { startRequested = false; }
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private IncidentReport report;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ApprovalDecision approval = ApprovalDecision.NONE;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private IncidentClassification classification;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private EvidenceBundle evidence;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(nullable = false, length = 4000, updatable = false) private String question;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private InvestigationStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected IncidentInvestigation() { }

    public IncidentInvestigation(UUID ownerId, String question) {
        this.ownerId = java.util.Objects.requireNonNull(ownerId);
        if (question == null || question.isBlank() || question.length() > 4000)
            throw new IllegalArgumentException("Question must contain 1–4000 characters");
        this.question = question.strip();
        this.status = InvestigationStatus.CREATED;
    }

    @PrePersist void initialize() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getQuestion() { return question; }
    public InvestigationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public IncidentClassification getClassification() { return classification; }
    public EvidenceBundle getEvidence() { return evidence; }
    public IncidentReport getReport() { return report; }
    public ApprovalDecision getApproval() { return approval; }
    public void report(IncidentReport value, boolean needsApproval) {
        if (status != InvestigationStatus.REASONING) throw new IllegalStateException("Investigation is not reasoning");
        report = java.util.Objects.requireNonNull(value);
        approval = needsApproval ? ApprovalDecision.PENDING : ApprovalDecision.NONE;
        if (needsApproval) approvalRequestedAt = Instant.now();
        transition(needsApproval ? InvestigationStatus.WAITING_APPROVAL : InvestigationStatus.COMPLETED);
    }
    public void decide(ApprovalDecision decision) {
        if (status != InvestigationStatus.WAITING_APPROVAL || approval != ApprovalDecision.PENDING)
            throw new IllegalStateException("Approval is not pending");
        if (decision != ApprovalDecision.APPROVED && decision != ApprovalDecision.DENIED && decision != ApprovalDecision.TIMED_OUT)
            throw new IllegalArgumentException("Invalid approval decision");
        approval = decision;
    }
    public void completeAction() { transition(InvestigationStatus.COMPLETED); }
    public void fail() { transition(InvestigationStatus.FAILED); }
    public void beginAnalysis() { transition(InvestigationStatus.ANALYZING); }
    public void classify(IncidentClassification value) {
        if (status != InvestigationStatus.ANALYZING) throw new IllegalStateException("Investigation is not analyzing");
        classification = java.util.Objects.requireNonNull(value);
        transition(InvestigationStatus.COLLECTING_EVIDENCE);
    }
    public void collect(EvidenceBundle value) {
        if (status != InvestigationStatus.COLLECTING_EVIDENCE) throw new IllegalStateException("Investigation is not collecting evidence");
        evidence = java.util.Objects.requireNonNull(value);
        transition(InvestigationStatus.REASONING);
    }
    public void evidenceFailure(EvidenceBundle value) {
        if (status != InvestigationStatus.COLLECTING_EVIDENCE) throw new IllegalStateException("Investigation is not collecting evidence");
        evidence = java.util.Objects.requireNonNull(value);
    }
    private void transition(InvestigationStatus next) {
        boolean allowed = switch (status) {
            case CREATED -> next == InvestigationStatus.ANALYZING;
            case ANALYZING -> next == InvestigationStatus.COLLECTING_EVIDENCE;
            case COLLECTING_EVIDENCE -> next == InvestigationStatus.REASONING;
            case REASONING -> next == InvestigationStatus.WAITING_APPROVAL || next == InvestigationStatus.COMPLETED;
            case WAITING_APPROVAL -> next == InvestigationStatus.COMPLETED;
            case COMPLETED, FAILED -> false;
        };
        if (!allowed && next != InvestigationStatus.FAILED) throw new IllegalStateException("Invalid investigation transition");
        if (status == InvestigationStatus.COMPLETED || status == InvestigationStatus.FAILED) throw new IllegalStateException("Terminal investigation");
        status = next;
    }
}
