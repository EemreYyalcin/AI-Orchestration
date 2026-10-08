package dev.orchestrationlab.incident.reasoning.application;
import dev.orchestrationlab.incident.context.application.EvidenceBundle;
public interface IncidentSynthesisModel {
    IncidentSynthesis synthesize(String question, EvidenceBundle evidence);
}
