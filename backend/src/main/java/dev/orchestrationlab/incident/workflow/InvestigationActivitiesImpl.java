package dev.orchestrationlab.incident.workflow;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import dev.orchestrationlab.incident.investigation.application.*;
import dev.orchestrationlab.incident.investigation.domain.*;
import dev.orchestrationlab.incident.orchestration.application.*;
import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.context.application.ContextBuilder;

@Component("investigationActivities") @Profile("temporal")
public class InvestigationActivitiesImpl implements InvestigationActivities {
    private final InvestigationService investigations;
    private final InvestigationStateService state;
    private final ClassificationService classifier;
    private final WorkflowRoutingPolicy routes;
    private final ContextBuilder context;
    private final EvidenceCollector collector;
    private final SynthesisService synthesis;
    public InvestigationActivitiesImpl(InvestigationService investigations, InvestigationStateService state, ClassificationService classifier,
            WorkflowRoutingPolicy routes, ContextBuilder context, EvidenceCollector collector, SynthesisService synthesis) {
        this.investigations = investigations; this.state = state; this.classifier = classifier; this.routes = routes;
        this.context = context; this.collector = collector; this.synthesis = synthesis;
    }
    @Override public void classify(UUID owner, UUID id) {
        var view = investigations.read(owner, id);
        if (view.status() == InvestigationStatus.CREATED) state.begin(owner, id);
        if (investigations.read(owner, id).status() == InvestigationStatus.ANALYZING)
            state.classification(owner, id, classifier.classify(view.question()).classification());
    }
    @Override public void collect(UUID owner, UUID id) {
        var view = investigations.read(owner, id);
        if (view.status() == InvestigationStatus.COLLECTING_EVIDENCE) {
            try { state.evidence(owner, id, collector.collect(owner, id, context.select(routes.route(view.classification())))); }
            catch (EvidenceCollector.RequiredEvidenceUnavailable failure) {
                state.evidenceFailure(owner, id, failure.evidence()); throw failure;
            }
        }
    }
    @Override public boolean analyze(UUID owner, UUID id) {
        var view = investigations.read(owner, id);
        if (view.status() == InvestigationStatus.REASONING) {
            var result = synthesis.analyze(view.question(), view.evidence());
            boolean approval = result.synthesis().proposedAction() == IncidentSynthesis.ProposedAction.RESTART_RECOMMENDATION_SERVICE;
            if (approval && (result.degraded() || !view.question().toLowerCase(java.util.Locale.ROOT).contains("recommendation")))
                throw new IllegalArgumentException("Unsupported remediation proposal");
            state.report(owner, id, new IncidentReport(id, view.classification(), view.evidence(), result.synthesis(),
                    result.degraded() || view.evidence().degraded() || view.classification().equals(IncidentClassification.fallback()), true), approval);
        }
        return investigations.read(owner, id).status() == InvestigationStatus.WAITING_APPROVAL;
    }
    @Override public void complete(UUID owner, UUID id, ApprovalDecision decision) { state.decision(owner, id, decision); state.finish(owner, id); }
    @Override public void fail(UUID owner, UUID id) { state.fail(owner, id); }
}
