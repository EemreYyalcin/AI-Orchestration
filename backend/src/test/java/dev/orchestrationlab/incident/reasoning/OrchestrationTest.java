package dev.orchestrationlab.incident.reasoning;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.context.application.*;
import dev.orchestrationlab.incident.orchestration.application.SynthesisService;
import dev.orchestrationlab.incident.tool.application.ToolKind;
import static org.assertj.core.api.Assertions.*;

class OrchestrationTest {
    @Test void fanOutRunsIndependentReadsBeforeFanInAndOptionalFailureIsDegraded() throws Exception {
        var execution = org.mockito.Mockito.mock(dev.orchestrationlab.incident.tool.application.ToolExecutionService.class);
        var entered = new java.util.concurrent.CountDownLatch(2);
        org.mockito.Mockito.when(execution.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    entered.countDown(); assertThat(entered.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    var source = (ToolKind) call.getArgument(1);
                    return source == ToolKind.LOGS ? new dev.orchestrationlab.incident.tool.application.ToolResult(source, List.of("observed error"), null)
                            : dev.orchestrationlab.incident.tool.application.ToolResult.failed(source, dev.orchestrationlab.incident.tool.application.ToolResult.Failure.UNAVAILABLE);
                });
        var builder = new ContextBuilder(new ContextBudget(12, 2000, 8000, 4));
        var collector = new dev.orchestrationlab.incident.orchestration.application.EvidenceCollector(execution, builder);
        try {
            var bundle = collector.collect(UUID.randomUUID(), UUID.randomUUID(), new ContextSelection(List.of(ToolKind.LOGS, ToolKind.DOCUMENTATION), Set.of(ToolKind.LOGS)));
            assertThat(bundle.items()).hasSize(1); assertThat(bundle.degraded()).isTrue();
            org.mockito.Mockito.verify(execution, org.mockito.Mockito.times(3)).execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        } finally {
            var close = collector.getClass().getDeclaredMethod("close"); close.setAccessible(true); close.invoke(collector);
        }
    }
    @Test void invalidCitationsRetryThenUseExplicitConservativeFallback() {
        var bundle = new EvidenceBundle(List.of(new Evidence("LOGS-1", ToolKind.LOGS, "observed timeout")), List.of(), false);
        var attempts = new AtomicInteger();
        IncidentSynthesisModel primary = (q, e) -> { attempts.incrementAndGet(); return answer("forged", IncidentSynthesis.ProposedAction.RESTART_RECOMMENDATION_SERVICE); };
        var result = SynthesisService.analyze("question", bundle, primary, (q, e) -> answer("LOGS-1", IncidentSynthesis.ProposedAction.NONE));
        assertThat(attempts.get()).isEqualTo(2); assertThat(result.degraded()).isTrue();
        assertThat(result.synthesis().proposedAction()).isEqualTo(IncidentSynthesis.ProposedAction.NONE);
    }
    private IncidentSynthesis answer(String id, IncidentSynthesis.ProposedAction action) {
        return new IncidentSynthesis("bounded answer", List.of(), List.of(), Set.of(id), action);
    }
}
