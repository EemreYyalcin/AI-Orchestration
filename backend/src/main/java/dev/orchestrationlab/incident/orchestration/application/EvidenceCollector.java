package dev.orchestrationlab.incident.orchestration.application;

import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import dev.orchestrationlab.incident.context.application.*;
import dev.orchestrationlab.incident.tool.application.*;

/**
 * Seçilmiş bağımsız salt okunur kanıt kaynaklarını paralel okur ve tek bütçeli pakette birleştirir.
 * Dört worker ve 32 kuyruk sınırı vardır; araç yürütme havuzundan ayrıdır. Zorunlu kaynak yokluğu
 * incelemeyi durdurur, isteğe bağlı hata kaliteyi düşürür.
 */
@Service
public class EvidenceCollector {
    private final ToolExecutionService tools;
    private final ContextBuilder context;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    private final ExecutorService executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), Thread.ofVirtual().name("evidence-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    /**
     * Araç servisi, bağlam bütçeleme ve isteğe bağlı telemetriyi bağlar; kısa constructor testlerde
     * telemetrisiz kullanımı sağlar.
     */
    public EvidenceCollector(ToolExecutionService tools, ContextBuilder context) { this(tools, context, null); }
    /**
     * Araç servisi, bağlam bütçeleme ve isteğe bağlı telemetriyi bağlar; kısa constructor testlerde
     * telemetrisiz kullanımı sağlar.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public EvidenceCollector(ToolExecutionService tools, ContextBuilder context, dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        this.tools = tools; this.context = context; this.telemetry = telemetry;
    }
    /**
     * Sahiplik ve seçilmiş kaynaklardan güvenilir ToolContext oluşturur. Mevcut hedef
     * recommendation/ERRORS/4 sabittir. TIMEOUT/UNAVAILABLE için bir tekrar yapar, toplam
     * birleştirmeyi 6 saniyeyle sınırlar. Paket oluşturulduktan sonra zorunlu kaynak eksikse paketi
     * taşıyan exception fırlatır.
     * @param owner Güvenilir dahili kullanıcı UUID’si; istek gövdesi/model argümanı değildir.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
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
    /**
     * Spring kapanışında toplama havuzunu durdurur ve bekleyen işleri kesmeyi ister.
     */
    @PreDestroy void close() { executor.shutdownNow(); }
    /**
     * Zorunlu kanıt bulunmadığında kısmi paketi taşıyan hata türüdür; çağıran bu paketi kaydedip
     * yürütmeyi yeniden deneyebilir veya sonlandırabilir.
     */
    public static final class RequiredEvidenceUnavailable extends RuntimeException {
        private final EvidenceBundle evidence;
        /**
         * Hata mesajını ve eldeki kısmi kanıt paketini taşır; başarısız toplama sonrası kalıcılık için
         * paket korunur.
         */
        public RequiredEvidenceUnavailable(EvidenceBundle evidence) { super("Required evidence unavailable"); this.evidence = evidence; }
        /**
         * Zorunlu kaynak eksikliğinde elde edilen kısmi paketi döndürür; çağıran hata durumunu ve
         * mevcut kanıtı kalıcılaştırabilir.
         */
        public EvidenceBundle evidence() { return evidence; }
    }
}
