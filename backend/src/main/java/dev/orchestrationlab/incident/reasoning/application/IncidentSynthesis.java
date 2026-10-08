package dev.orchestrationlab.incident.reasoning.application;

import java.util.*;

public record IncidentSynthesis(String summary, List<String> likelyCauses, List<String> recommendedActions,
                                Set<String> evidenceIds, ProposedAction proposedAction) {
    public enum ProposedAction { NONE, RESTART_RECOMMENDATION_SERVICE }
    public IncidentSynthesis {
        if (summary == null || summary.isBlank() || summary.length() > 1500) throw new IllegalArgumentException("Invalid summary");
        likelyCauses = bounded(likelyCauses); recommendedActions = bounded(recommendedActions);
        if (evidenceIds == null || evidenceIds.size() > 100 || evidenceIds.stream().anyMatch(id -> id == null || id.length() > 64))
            throw new IllegalArgumentException("Invalid citations");
        evidenceIds = Set.copyOf(evidenceIds); Objects.requireNonNull(proposedAction);
    }
    private static List<String> bounded(List<String> values) {
        if (values == null || values.size() > 5 || values.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 500))
            throw new IllegalArgumentException("Invalid conclusions");
        return List.copyOf(values);
    }
}
