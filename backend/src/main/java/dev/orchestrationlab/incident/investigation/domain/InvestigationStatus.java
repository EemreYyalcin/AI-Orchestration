package dev.orchestrationlab.incident.investigation.domain;

public enum InvestigationStatus {
    CREATED, ANALYZING, COLLECTING_EVIDENCE, REASONING, WAITING_APPROVAL, COMPLETED, FAILED
}
