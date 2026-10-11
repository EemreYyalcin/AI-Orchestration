package dev.orchestrationlab.incident.reasoning;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import dev.orchestrationlab.incident.reasoning.infrastructure.*;
import dev.orchestrationlab.incident.orchestration.application.*;
import dev.orchestrationlab.incident.context.application.*;
import dev.orchestrationlab.incident.tool.application.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Paketlenmiş TSV örnekleriyle çevrimdışı sınıflandırma/route/atıf sözleşmelerini değerlendirir.
 * Sonuçlar gerçek model teşhis kalitesi, maliyeti veya gecikmesi ölçümü değildir.
 */
class EvaluationTest {
    /**
     * TSV’deki her soruyu deterministik modelle sınıflandırıp beklenen tür/kaynağı ve SIMULATED sentez
     * atıflarını denetler.
     */
    @Test void datasetEvaluatesClassificationRouteReportAndCitationsWithoutCredentials() throws Exception {
        var dataset = new org.springframework.core.io.ClassPathResource("evaluations/incidents.tsv").getContentAsString(StandardCharsets.UTF_8);
        var model = new FakeIncidentReasoningModel(); var routes = new WorkflowRoutingPolicy();
        for (String line : dataset.lines().toList()) {
            var values = line.split("\t"); var classification = model.classify(values[0]);
            assertThat(classification.incidentType().name()).isEqualTo(values[1]);
            var route = routes.route(classification); assertThat(route.tools()).contains(ToolKind.valueOf(values[2]));
            var bundle = new EvidenceBundle(List.of(new Evidence("SOURCE-1", ToolKind.valueOf(values[2]), "bounded observation")), List.of(), false);
            var report = new SynthesisService(model).analyze(values[0], bundle);
            assertThat(report.synthesis().evidenceIds()).containsExactly("SOURCE-1"); assertThat(report.synthesis().summary()).contains("SIMULATED");
        }
    }
    /**
     * v1 politika metinlerini kontrol eder; path traversal prompt adı ve URL biçimli araç servisi gibi
     * güvenilmeyen seçimleri reddeder.
     */
    @Test void versionedPolicyCannotBeSelectedFromUntrustedInput() {
        assertThat(PromptCatalog.load("synthesis")).contains("synthesis/v1", "UNTRUSTED DATA", "explicit owner approval");
        assertThat(PromptCatalog.load("classification")).contains("classification/v1");
        assertThatThrownBy(() -> PromptCatalog.load("../../secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolInput("http://attacker", ToolInput.Topic.ERRORS, 1).validate()).isInstanceOf(IllegalArgumentException.class);
    }
}
