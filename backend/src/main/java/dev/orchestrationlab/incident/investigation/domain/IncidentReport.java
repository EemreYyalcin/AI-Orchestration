package dev.orchestrationlab.incident.investigation.domain;
import java.util.UUID;
import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.context.application.EvidenceBundle;
public record IncidentReport(UUID investigationId, IncidentClassification classification, EvidenceBundle evidence,
                             IncidentSynthesis findings, boolean degraded, boolean diagnosticSourcesSimulated) { }
