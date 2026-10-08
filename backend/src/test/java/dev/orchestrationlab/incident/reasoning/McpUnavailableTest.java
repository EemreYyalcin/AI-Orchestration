package dev.orchestrationlab.incident.reasoning;

import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import dev.orchestrationlab.incident.investigation.domain.IncidentInvestigation;
import dev.orchestrationlab.incident.investigation.repository.InvestigationRepository;
import dev.orchestrationlab.incident.tool.application.*;
import dev.orchestrationlab.incident.tool.infrastructure.McpDocumentationAdapter;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpUnavailableTest {
    @Test void unavailableOptionalMcpIsBoundedEvidenceFailure() {
        var repository = mock(InvestigationRepository.class);
        UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        when(repository.findByIdAndOwnerId(id, owner)).thenReturn(Optional.of(new IncidentInvestigation(owner, "question")));
        var execution = new ToolExecutionService(List.of(new McpDocumentationAdapter("http://localhost:1")), repository, Duration.ofSeconds(2));
        try {
            var result = execution.execute(new ToolContext(owner, id, Set.of(ToolKind.DOCUMENTATION)), ToolKind.DOCUMENTATION,
                    new ToolInput("recommendation", ToolInput.Topic.RUNBOOK, 3));
            assertThat(result.failure()).isIn(ToolResult.Failure.UNAVAILABLE, ToolResult.Failure.TIMEOUT);
            assertThat(result.evidence()).isEmpty();
        } finally { execution.close(); }
    }
}
