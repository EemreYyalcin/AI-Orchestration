package dev.orchestrationlab.incident.orchestration.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import dev.orchestrationlab.incident.investigation.application.*;
import dev.orchestrationlab.incident.investigation.domain.*;
import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.context.application.ContextBuilder;

@Service
@org.springframework.context.annotation.Profile("!temporal")
public class IncidentOrchestrator implements InvestigationExecution {
    private final InvestigationService investigations;
    private final InvestigationStateService state;
    private final ClassificationService classifier;
    private final ContextBuilder context;
    private final EvidenceCollector collector;
    private final SynthesisService synthesis;
    public IncidentOrchestrator(InvestigationService investigations, InvestigationStateService state, ClassificationService classifier,
                                ContextBuilder context, EvidenceCollector collector, SynthesisService synthesis) {
        this.investigations = investigations; this.state = state; this.classifier = classifier;
        this.context = context; this.collector = collector; this.synthesis = synthesis;
    }
    public InvestigationService.InvestigationView run(UUID owner, UUID id) {
        var original = investigations.read(owner, id);
        if (original.status() != InvestigationStatus.CREATED) throw new IllegalStateException("Investigation already started");
        state.begin(owner, id);
        try {
            var classified = classifier.classify(original.question()); state.classification(owner, id, classified.classification());
            var evidence = collector.collect(owner, id, context.select(classified.route())); state.evidence(owner, id, evidence);
            var analyzed = synthesis.analyze(original.question(), evidence);
            boolean approve = !analyzed.degraded() && analyzed.synthesis().proposedAction() == IncidentSynthesis.ProposedAction.RESTART_RECOMMENDATION_SERVICE
                    && original.question().toLowerCase(java.util.Locale.ROOT).contains("recommendation");
            // Model proposal is subject to Java policy. An unsupported target cannot be approved.
            if (!approve && analyzed.synthesis().proposedAction() != IncidentSynthesis.ProposedAction.NONE)
                throw new IllegalArgumentException("Unsupported remediation target");
            state.report(owner, id, new IncidentReport(id, classified.classification(), evidence, analyzed.synthesis(),
                    classified.degraded() || evidence.degraded() || analyzed.degraded(), true), approve);
        } catch (EvidenceCollector.RequiredEvidenceUnavailable failure) {
            state.evidenceFailure(owner, id, failure.evidence()); state.fail(owner, id);
        } catch (RuntimeException failure) { state.fail(owner, id); }
        return investigations.read(owner, id);
    }
    public InvestigationService.InvestigationView decide(UUID owner, UUID id, ApprovalDecision decision) {
        state.decision(owner, id, decision); state.finish(owner, id); return investigations.read(owner, id);
    }
}
