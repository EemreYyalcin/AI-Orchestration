package dev.orchestrationlab.incident.reasoning;

import java.util.*;
import org.junit.jupiter.api.Test;
import dev.orchestrationlab.incident.context.application.*;
import dev.orchestrationlab.incident.orchestration.application.WorkflowRoutingPolicy;
import dev.orchestrationlab.incident.tool.application.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Bağlam seçimi, kanıt bütçeleri, maskeleme, hata türü ve kırpma bilgisini dış servis olmadan sınar.
 */
class ContextTest {
    /**
     * Araç bütçesi küçültülünce zorunlu METRICS kaynağının ilk seçildiğini ve toplam kaynak sayısının
     * sınırlandığını doğrular.
     */
    @Test void selectionPrioritizesRequiredSourcesAndHonorsToolBudget() {
        var builder = new ContextBuilder(new ContextBudget(3, 100, 150, 2));
        var selection = builder.select(new WorkflowRoutingPolicy.Route(Set.of(ToolKind.values()), Set.of(ToolKind.METRICS)));
        assertThat(selection.sources()).hasSize(2).startsWith(ToolKind.METRICS);
        assertThat(selection.required()).containsExactly(ToolKind.METRICS);
    }
    /**
     * Öğe/kaynak/toplam karakter bütçelerinin birlikte uygulandığını, seçilmeyen kaynağın atıldığını
     * ve kırpmanın açıkça raporlandığını sınar.
     */
    @Test void evidenceHasSourceItemAndTotalBoundsWithExplicitTruncation() {
        var builder = new ContextBuilder(new ContextBudget(3, 100, 150, 2));
        var selection = new ContextSelection(List.of(ToolKind.LOGS, ToolKind.DATABASE), Set.of(ToolKind.LOGS));
        var bundle = builder.build(selection, List.of(new ToolResult(ToolKind.LOGS, Collections.nCopies(5, "x".repeat(70)), null),
                new ToolResult(ToolKind.DATABASE, List.of("y".repeat(200)), null),
                new ToolResult(ToolKind.METRICS, List.of("irrelevant"), null)));
        assertThat(bundle.items()).hasSize(3);
        assertThat(bundle.items().stream().mapToInt(item -> item.summary().length()).sum()).isEqualTo(150);
        assertThat(bundle.items()).allSatisfy(item -> assertThat(item.summary().length()).isLessThanOrEqualTo(100));
        assertThat(bundle.truncated()).isTrue();
        assertThat(bundle.items()).noneMatch(item -> item.source() == ToolKind.METRICS);
    }
    /**
     * Parola, anahtar ve e-postanın maskelendiğini; dokümantasyon timeout’unun tipli ve degraded
     * olarak görünür olduğunu doğrular.
     */
    @Test void sanitizationPrecedesTruncationAndFailureIsTyped() {
        var builder = new ContextBuilder(new ContextBudget(3, 100, 150, 2));
        var bundle = builder.build(new ContextSelection(List.of(ToolKind.LOGS, ToolKind.DOCUMENTATION), Set.of(ToolKind.LOGS)),
                List.of(new ToolResult(ToolKind.LOGS, List.of("password=privateValue api_key=topsecret user@example.com"), null),
                        ToolResult.failed(ToolKind.DOCUMENTATION, ToolResult.Failure.TIMEOUT)));
        assertThat(builder.render(bundle)).contains("[REDACTED]", "[EMAIL]", "UNTRUSTED EVIDENCE", "TIMEOUT")
                .doesNotContain("privateValue", "topsecret", "user@example.com");
        assertThat(bundle.degraded()).isTrue();
    }
    /**
     * Sıfır öğe veya dörtten fazla araç bütçesinin constructor’da reddedildiğini sınar.
     */
    @Test void invalidBudgetsFailAtConfigurationTime() {
        assertThatThrownBy(() -> new ContextBudget(0, 100, 100, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextBudget(10, 100, 100, 5)).isInstanceOf(IllegalArgumentException.class);
    }
}
