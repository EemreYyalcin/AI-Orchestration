package dev.orchestrationlab.incident.orchestration.application;

import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import dev.orchestrationlab.incident.context.application.*;
import dev.orchestrationlab.incident.tool.application.*;

@Service
public class EvidenceCollector {
    private final ToolExecutionService tools;
    private final ContextBuilder context;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    private final ExecutorService executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), Thread.ofVirtual().name("evidence-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    public EvidenceCollector(ToolExecutionService tools, ContextBuilder context) { this(tools, context, null); }
    @org.springframework.beans.factory.annotation.Autowired
    public EvidenceCollector(ToolExecutionService tools, ContextBuilder context, dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        this.tools = tools; this.context = context; this.telemetry = telemetry;
    }
    public EvidenceBundle collect(UUID owner, UUID id, ContextSelection selection) {
        List<Future<ToolResult>> pending = new ArrayList<>();
        var policy = new ToolContext(owner, id, Set.copyOf(selection.sources()));
        for (var source : selection.sources()) {
            try { pending.add(executor.submit(io.micrometer.context.ContextSnapshot.captureAll().wrap((Callable<ToolResult>) () -> {
                ToolResult result = tools.execute(policy, source, new ToolInput("recommendation", ToolInput.Topic.ERRORS, 4));
                if (result.failure() == ToolResult.Failure.UNAVAILABLE || result.failure() == ToolResult.Failure.TIMEOUT) {
                    if (telemetry != null) telemetry.count("incident.retries", "tool");
                    result = tools.execute(policy, source, new ToolInput("recommendation", ToolInput.Topic.ERRORS, 4));
                }
                return result;
            }))); } catch (RejectedExecutionException busy) { pending.add(CompletableFuture.completedFuture(ToolResult.failed(source, ToolResult.Failure.BUSY))); }
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        List<ToolResult> results = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            try { results.add(pending.get(i).get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); pending.forEach(f -> f.cancel(true)); throw new IllegalStateException("Evidence interrupted"); }
            catch (ExecutionException | TimeoutException failed) { pending.get(i).cancel(true); results.add(ToolResult.failed(selection.sources().get(i), ToolResult.Failure.TIMEOUT)); }
        }
        EvidenceBundle bundle = context.build(selection, results);
        if (selection.required().stream().anyMatch(required -> bundle.items().stream().noneMatch(e -> e.source() == required)))
            throw new RequiredEvidenceUnavailable(bundle);
        return bundle;
    }
    @PreDestroy void close() { executor.shutdownNow(); }
    public static final class RequiredEvidenceUnavailable extends RuntimeException {
        private final EvidenceBundle evidence;
        public RequiredEvidenceUnavailable(EvidenceBundle evidence) { super("Required evidence unavailable"); this.evidence = evidence; }
        public EvidenceBundle evidence() { return evidence; }
    }
}
