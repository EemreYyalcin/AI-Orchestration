package dev.orchestrationlab.incident.investigation;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import dev.orchestrationlab.incident.user.domain.*;
import dev.orchestrationlab.incident.user.repository.AppUserRepository;
import dev.orchestrationlab.incident.investigation.application.InvestigationService;
import java.util.UUID;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties = {"spring.temporal.test-server.enabled=true", "app.workflow.dispatch-delay=60000"})
@ActiveProfiles("temporal") @AutoConfigureMockMvc
@Import(DurableApiIntegrationTest.Database.class)
class DurableApiIntegrationTest {
    @TestConfiguration(proxyBeanMethods = false) static class Database {
        @Bean @ServiceConnection PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:18"); }
    }
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired InvestigationService service;
    @Autowired JdbcTemplate jdbc;
    @Test void authenticatedCreateStartsDurableWorkflowOwnerApprovesAndReportIsPersisted() throws Exception {
        UUID owner = users.saveAndFlush(new AppUser(UserProvider.GOOGLE, "durable-owner", null, null, null)).getId();
        var response = mvc.perform(post("/api/investigations").with(authentication(InvestigationIntegrationTest.auth(owner))).with(csrf())
                .contentType("application/json").content("{\"question\":\"recommendation database connection exhaustion\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.parse(response.getResponse().getContentAsString()).read("$.id"));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> service.read(owner, id).status().name().equals("WAITING_APPROVAL"));
        mvc.perform(post("/api/investigations/" + id + "/approve").with(authentication(InvestigationIntegrationTest.auth(UUID.randomUUID()))).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/investigations/" + id + "/approve").with(authentication(InvestigationIntegrationTest.auth(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/investigations/" + id + "/approve").with(authentication(InvestigationIntegrationTest.auth(owner))).with(csrf()))
                .andExpect(status().isOk());
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> service.read(owner, id).status().name().equals("COMPLETED"));
        assertThat(service.read(owner, id).report().findings().evidenceIds()).isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mock_remediation_actions WHERE investigation_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM investigation_audit WHERE investigation_id = ?", Integer.class, id)).isGreaterThanOrEqualTo(5);
        mvc.perform(get("/api/investigations/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
