package dev.orchestrationlab.incident.investigation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import dev.orchestrationlab.incident.investigation.application.InvestigationService;
import dev.orchestrationlab.incident.orchestration.application.IncidentOrchestrator;
import dev.orchestrationlab.incident.user.domain.*;
import dev.orchestrationlab.incident.user.repository.AppUserRepository;
import static org.assertj.core.api.Assertions.*;
/** Explicit optional smoke: requires separately running documentation MCP on localhost:8081. */
@SpringBootTest @ActiveProfiles("mcp") @Import(InvestigationIntegrationTest.Database.class)
class McpInvestigationSmoke {
    @Autowired InvestigationService investigations;
    @Autowired IncidentOrchestrator orchestrator;
    @Autowired AppUserRepository users;
    @Test void remoteRunbookIsCollectedIntoPersistedInvestigationContext() {
        var owner = users.saveAndFlush(new AppUser(UserProvider.GOOGLE, "mcp-smoke", null, null, null)).getId();
        var investigation = investigations.create(owner, "recommendation database connection pool failing");
        var report = orchestrator.run(owner, investigation.id());
        assertThat(report.evidence()).isNotNull();
        assertThat(report.evidence().items()).anySatisfy(item -> {
            assertThat(item.source().name()).isEqualTo("DOCUMENTATION"); assertThat(item.summary()).contains("runbook:recommendation");
        });
        assertThat(investigations.read(owner, investigation.id()).evidence()).isEqualTo(report.evidence());
    }
}
