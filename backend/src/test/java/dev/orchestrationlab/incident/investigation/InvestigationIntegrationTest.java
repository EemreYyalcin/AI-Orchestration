package dev.orchestrationlab.incident.investigation;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import dev.orchestrationlab.incident.authentication.application.ApplicationPrincipal;
import dev.orchestrationlab.incident.user.domain.*;
import dev.orchestrationlab.incident.user.repository.AppUserRepository;
import dev.orchestrationlab.incident.investigation.application.InvestigationService;
import dev.orchestrationlab.incident.investigation.repository.InvestigationRepository;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(InvestigationIntegrationTest.Database.class)
class InvestigationIntegrationTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean @ServiceConnection PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:18"); }
    }
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired InvestigationRepository investigations;
    @Autowired InvestigationService service;
    @Autowired dev.orchestrationlab.incident.investigation.application.InvestigationStateService state;
    @Autowired dev.orchestrationlab.incident.orchestration.application.IncidentOrchestrator orchestrator;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    UUID owner;
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM investigation_audit");
        jdbc.update("DELETE FROM mock_remediation_actions");
        investigations.deleteAllInBatch();
        users.deleteAllInBatch();
        owner = users.saveAndFlush(new AppUser(UserProvider.GOOGLE, "owner", null, null, null)).getId();
    }
    @Test void createPersistsAndOnlyOwnerCanRead() throws Exception {
        var result = mvc.perform(post("/api/investigations").with(authentication(auth(owner))).with(csrf())
                        .contentType("application/json").content("{\"question\":\"Why is recommendation failing?\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.ownerId").doesNotExist()).andReturn();
        String id = com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString()).read("$.id");
        assertThat(investigations.findById(UUID.fromString(id)).orElseThrow().getOwnerId()).isEqualTo(owner);
        mvc.perform(get("/api/investigations/" + id).with(authentication(auth(owner))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/investigations/" + id).with(authentication(auth(UUID.randomUUID()))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/investigations/" + id)).andExpect(status().isUnauthorized());
    }
    @Test void unsafeEndpointRequiresCsrfAndAuthentication() throws Exception {
        mvc.perform(post("/api/investigations").with(authentication(auth(owner)))
                .contentType("application/json").content("{\"question\":\"test\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/investigations").with(csrf())
                .contentType("application/json").content("{\"question\":\"test\"}")).andExpect(status().isUnauthorized());
        assertThat(investigations.count()).isZero();
    }
    @Test void rejectsEmptyOversizedAndStaleOwner() {
        assertThatThrownBy(() -> service.create(owner, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(owner, "x".repeat(4001))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), "question"))
                .isInstanceOf(InvestigationService.MissingOwner.class);
        assertThat(investigations.count()).isZero();
    }
    @Test void invalidQuestionReturnsSafe400() throws Exception {
        mvc.perform(post("/api/investigations").with(authentication(auth(owner))).with(csrf())
                .contentType("application/json").content("{\"question\":null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Invalid investigation request"));
    }
    @Test void typedContextAndClassificationRoundTripThroughPostgresWithStrictTransitions() {
        var investigation = service.create(owner, "database pool exhausted");
        var classification = new dev.orchestrationlab.incident.reasoning.application.IncidentClassification(
                dev.orchestrationlab.incident.reasoning.application.IncidentType.DATABASE,
                dev.orchestrationlab.incident.reasoning.application.Severity.HIGH,
                java.util.Set.of(dev.orchestrationlab.incident.tool.application.ToolKind.DATABASE), "pool exhausted");
        var bundle = new dev.orchestrationlab.incident.context.application.EvidenceBundle(List.of(
                new dev.orchestrationlab.incident.context.application.Evidence("DATABASE-1",
                        dev.orchestrationlab.incident.tool.application.ToolKind.DATABASE, "sanitized pool snapshot")), List.of(), false);
        assertThatThrownBy(() -> state.classification(owner, investigation.id(), classification)).isInstanceOf(IllegalStateException.class);
        state.begin(owner, investigation.id());
        state.classification(owner, investigation.id(), classification);
        state.evidence(owner, investigation.id(), bundle);
        var restored = service.read(owner, investigation.id());
        assertThat(restored.classification()).isEqualTo(classification);
        assertThat(restored.evidence()).isEqualTo(bundle);
        assertThat(restored.status()).isEqualTo(dev.orchestrationlab.incident.investigation.domain.InvestigationStatus.REASONING);
        assertThatThrownBy(() -> state.begin(owner, investigation.id())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> state.begin(UUID.randomUUID(), investigation.id())).isInstanceOf(InvestigationService.NotFound.class);
    }
    @Test void investigationRequiresOwnerApprovalAndActionIsIdempotent() throws Exception {
        var created = service.create(owner, "recommendation database connection pool exhausted");
        var waiting = orchestrator.run(owner, created.id());
        assertThat(waiting.status().name()).isEqualTo("WAITING_APPROVAL");
        assertThat(waiting.report().findings().evidenceIds()).isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mock_remediation_actions", Integer.class)).isZero();
        mvc.perform(post("/api/investigations/" + created.id() + "/approve").with(authentication(auth(UUID.randomUUID()))).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/investigations/" + created.id() + "/approve").with(authentication(auth(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/investigations/" + created.id() + "/approve").with(authentication(auth(owner))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approval").value("APPROVED"));
        state.finish(owner, created.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mock_remediation_actions", Integer.class)).isEqualTo(1);
        assertThat(service.read(owner, created.id()).status().name()).isEqualTo("COMPLETED");
    }
    @Test void deniedActionCompletesWithoutExecution() {
        var created = service.create(owner, "recommendation database failing");
        orchestrator.run(owner, created.id());
        var result = orchestrator.decide(owner, created.id(), dev.orchestrationlab.incident.investigation.domain.ApprovalDecision.DENIED);
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.approval().name()).isEqualTo("DENIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mock_remediation_actions", Integer.class)).isZero();
    }
    @Test void requiredEvidenceFailureIsPersistedAndNoRemediationIsProposed() throws Exception {
        var execution = org.mockito.Mockito.mock(dev.orchestrationlab.incident.tool.application.ToolExecutionService.class);
        org.mockito.Mockito.when(execution.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> dev.orchestrationlab.incident.tool.application.ToolResult.failed(call.getArgument(1), dev.orchestrationlab.incident.tool.application.ToolResult.Failure.UNAVAILABLE));
        var model = new dev.orchestrationlab.incident.reasoning.infrastructure.FakeIncidentReasoningModel();
        var context = new dev.orchestrationlab.incident.context.application.ContextBuilder(new dev.orchestrationlab.incident.context.application.ContextBudget(12, 2000, 8000, 4));
        var collector = new dev.orchestrationlab.incident.orchestration.application.EvidenceCollector(execution, context);
        try {
            var fixture = new dev.orchestrationlab.incident.orchestration.application.IncidentOrchestrator(service, state,
                    new dev.orchestrationlab.incident.reasoning.application.ClassificationService(model, new dev.orchestrationlab.incident.orchestration.application.WorkflowRoutingPolicy()),
                    context, collector, new dev.orchestrationlab.incident.orchestration.application.SynthesisService(model));
            var created = service.create(owner, "recommendation database unavailable");
            var failed = fixture.run(owner, created.id());
            assertThat(failed.status().name()).isEqualTo("FAILED"); assertThat(failed.report()).isNull();
            assertThat(failed.evidence().failures()).anySatisfy(failure -> assertThat(failure.source().name()).isEqualTo("DATABASE"));
            assertThat(service.read(owner, created.id()).evidence()).isEqualTo(failed.evidence());
        } finally { var close = collector.getClass().getDeclaredMethod("close"); close.setAccessible(true); close.invoke(collector); }
    }
    @Test void startIntentCommitsTogetherWithInvestigationBeforeAnyTemporalCall() {
        var created = service.create(owner, "question", true);
        assertThat(jdbc.queryForObject("SELECT start_requested FROM incident_investigations WHERE id = ?", Boolean.class, created.id())).isTrue();
        assertThat(created.status().name()).isEqualTo("CREATED");
    }
    static UsernamePasswordAuthenticationToken auth(UUID id) {
        return UsernamePasswordAuthenticationToken.authenticated(new TestPrincipal(id), null, List.of());
    }
    record TestPrincipal(UUID getUserId) implements ApplicationPrincipal {
        @Override public String getName() { return getUserId.toString(); }
    }
}
