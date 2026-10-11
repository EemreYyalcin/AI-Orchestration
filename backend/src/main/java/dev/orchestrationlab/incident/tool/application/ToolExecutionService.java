package dev.orchestrationlab.incident.tool.application;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import dev.orchestrationlab.incident.investigation.repository.InvestigationRepository;
import dev.orchestrationlab.incident.context.application.EvidenceSanitizer;

/**
 * Her kanıt okumasında sahiplik, araç izni, tipli girdi, süre ve çıktı sınırlarını uygulayan merkezi
 * servistir. Dört sanal worker/32 kuyruk kullanır; ham adaptör hatalarını güvenli enum sonuçlarına
 * çevirir. Kendisi yeniden deneme yapmaz.
 */
@Service
public class ToolExecutionService {
    private final Map<ToolKind, EvidenceAdapter> adapters;
    private final InvestigationRepository investigations;
    private final Duration timeout;
    private final dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry;
    private final ExecutorService executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), Thread.ofVirtual().name("evidence-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    /**
     * Araç türlerini benzersiz adaptör haritasına bağlar; repository, pozitif süre sınırı ve isteğe
     * bağlı telemetriyi kurar. Kısa constructor telemetrisiz kullanım içindir.
     */
    public ToolExecutionService(List<EvidenceAdapter> adapters, InvestigationRepository investigations,
                                Duration timeout) { this(adapters, investigations, timeout, null); }
    /**
     * Araç türlerini benzersiz adaptör haritasına bağlar; repository, pozitif süre sınırı ve isteğe
     * bağlı telemetriyi kurar. Kısa constructor telemetrisiz kullanım içindir.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ToolExecutionService(List<EvidenceAdapter> adapters, InvestigationRepository investigations,
                                @Value("${app.tools.timeout:2s}") Duration timeout,
                                dev.orchestrationlab.incident.observability.InvestigationTelemetry telemetry) {
        this.telemetry = telemetry;
        this.adapters = adapters.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(EvidenceAdapter::kind, a -> a));
        this.investigations = investigations;
        this.timeout = timeout;
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Positive tool timeout required");
    }

    /**
     * Yetkili araç yürütmesini başlatır; telemetri varsa gözlem, çağrı sonucu ve süre metriği ekler.
     * Asıl politika executeInternal içinde tek yerde uygulanır.
     */
    public ToolResult execute(ToolContext context, ToolKind kind, ToolInput input) {
        if (telemetry == null) return executeInternal(context, kind, input);
        long started = System.nanoTime();
        var result = telemetry.observe("incident.tool", kind.name(), context.investigationId(), () -> executeInternal(context, kind, input));
        telemetry.tool(result, System.nanoTime() - started); return result;
    }
    /**
     * İzinli araç ve id/sahip eşleşmesini adaptörden önce kontrol eder, girdiyi doğrular ve sınırlı
     * havuzda okur. Çıktıyı input.limit öğe ve temizleme sonrası öğe başına 1000 karakterle sınırlar.
     * Kuyruk doluluğu, timeout, kesinti ve adaptör hatasını tipli sonuca çevirir; timeout işini iptal
     * etmeyi ister.
     */
    private ToolResult executeInternal(ToolContext context, ToolKind kind, ToolInput input) {
        // Modelin geçerli argüman vermesi yetki sağlamaz; her çağrıda izin ve sahiplik yeniden denetlenir.
        if (!context.allowedTools().contains(kind) || investigations.findByIdAndOwnerId(
                context.investigationId(), context.ownerId()).isEmpty())
            return ToolResult.failed(kind, ToolResult.Failure.FORBIDDEN);
        try { input.validate(); } catch (RuntimeException invalid) {
            return ToolResult.failed(kind, ToolResult.Failure.INVALID_ARGUMENT);
        }
        var adapter = adapters.get(kind);
        if (adapter == null) return ToolResult.failed(kind, ToolResult.Failure.UNAVAILABLE);
        Future<List<String>> task;
        try { task = executor.submit(io.micrometer.context.ContextSnapshot.captureAll().wrap((Callable<List<String>>) () -> adapter.read(input))); }
        catch (RejectedExecutionException busy) { return ToolResult.failed(kind, ToolResult.Failure.BUSY); }
        try {
            var evidence = task.get(timeout.toMillis(), TimeUnit.MILLISECONDS).stream().limit(input.limit())
                    .filter(Objects::nonNull).map(EvidenceSanitizer::sanitize)
                    .map(text -> text.substring(0, Math.min(text.length(), 1000))).toList();
            return new ToolResult(kind, evidence, null);
        } catch (TimeoutException expired) {
            task.cancel(true);
            return ToolResult.failed(kind, ToolResult.Failure.TIMEOUT);
        } catch (InterruptedException interrupted) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            return ToolResult.failed(kind, ToolResult.Failure.UNAVAILABLE);
        } catch (ExecutionException | RuntimeException unavailable) {
            return ToolResult.failed(kind, ToolResult.Failure.UNAVAILABLE);
        }
    }

    /**
     * Spring kapanışında araç havuzunu durdurur. Kesinti dış sistemlerdeki bütün yan işleri kesin
     * olarak sonlandırma garantisi değildir.
     */
    @PreDestroy public void close() { executor.shutdownNow(); }
}
