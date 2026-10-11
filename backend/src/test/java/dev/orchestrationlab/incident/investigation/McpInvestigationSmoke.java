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
/**
 * Açıkça seçilen yerel MCP/kalıcılık smoke kontrolüdür; standart test seçiminde çalışmaz.
 * Yapılandırılmış uzak runbook sunucusu ve PostgreSQL gerektirir; teşhis kaynakları simüle kalır.
 */
@SpringBootTest @ActiveProfiles("mcp") @Import(InvestigationIntegrationTest.Database.class)
class McpInvestigationSmoke {
    @Autowired InvestigationService investigations;
    @Autowired IncidentOrchestrator orchestrator;
    @Autowired AppUserRepository users;
    /**
     * Uzak MCP runbook kanıtının inceleme paketine ve kalıcı kayda geçtiğini doğrular; gerçek
     * operasyonel log veya canlı model kalitesi ölçmez.
     */
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
