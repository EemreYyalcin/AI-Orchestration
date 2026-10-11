package dev.orchestrationlab.incident.orchestration.application;

import java.util.*;
import org.springframework.stereotype.Component;
import dev.orchestrationlab.incident.reasoning.application.*;
import dev.orchestrationlab.incident.tool.application.ToolKind;

/**
 * Model sınıflandırmasını Java’nın sabit araç izinleri ve zorunlu kaynak kurallarıyla sınırlar. Model
 * iş akışı sınıfı veya rastgele çalıştırılabilir araç seçemez; yönlendirme yetkisi bu politikadadır.
 */
@Component
public class WorkflowRoutingPolicy {
    /**
     * Olay türüne göre izinli kümeyi denetler. DATABASE için DATABASE, PERFORMANCE için METRICS, diğer
     * türlerde LOGS zorunludur. Bu kaynağı ve DOCUMENTATION’ı modelin geçerli seçimlerine ekleyip
     * değişmez Route döndürür.
     */
    public Route route(IncidentClassification classification) {
        Set<ToolKind> allowed = switch (classification.incidentType()) {
            case DATABASE -> Set.of(ToolKind.LOGS, ToolKind.DATABASE, ToolKind.DOCUMENTATION, ToolKind.METRICS);
            case APPLICATION, UNKNOWN -> Set.of(ToolKind.LOGS, ToolKind.DOCUMENTATION);
            case DEPENDENCY, PERFORMANCE -> Set.of(ToolKind.LOGS, ToolKind.METRICS, ToolKind.DOCUMENTATION);
        };
        if (!allowed.containsAll(classification.requiredTools())) throw new IllegalArgumentException("Tool outside workflow policy");
        ToolKind required = switch (classification.incidentType()) {
            case DATABASE -> ToolKind.DATABASE;
            case PERFORMANCE -> ToolKind.METRICS;
            default -> ToolKind.LOGS;
        };
        var selected = EnumSet.copyOf(classification.requiredTools());
        selected.add(required);
        selected.add(ToolKind.DOCUMENTATION);
        return new Route(Set.copyOf(selected), Set.of(required));
    }
    /**
     * Java’nın izin verdiği toplam araç kümesi ile eksikliği yürütmeyi durduracak zorunlu kanıt
     * kümesini taşır; kümeler değişmez kopyalanır.
     * @param requiredEvidence Java’nın zorunlu tuttuğu kanıt kaynakları.
     */
    public record Route(Set<ToolKind> tools, Set<ToolKind> requiredEvidence) {
        /**
         * Seçilen araçları ve zorunlu kaynakları değişmez kümelere kopyalar.
         */
        public Route { tools = Set.copyOf(tools); requiredEvidence = Set.copyOf(requiredEvidence); }
    }
}
