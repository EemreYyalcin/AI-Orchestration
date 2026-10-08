package dev.orchestrationlab.incident.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.DockerClientFactory;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import dev.orchestrationlab.incident.investigation.domain.ApprovalDecision;
import java.sql.*;
import java.util.UUID;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

/** Explicit local smoke only: seeds disposable fixtures; restarts the local backend container. */
class ContainerStackSmoke {
    private Connection database() throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://localhost:5432/" + System.getenv("POSTGRES_DB"),
                System.getenv("POSTGRES_USER"), System.getenv("POSTGRES_PASSWORD"));
    }
    private String status(UUID id) throws SQLException {
        try (var connection = database(); var query = connection.prepareStatement("SELECT status FROM incident_investigations WHERE id = ?")) {
            query.setObject(1, id); try (var result = query.executeQuery()) { result.next(); return result.getString(1); }
        }
    }
    @Test @Timeout(120) void realStackCollectsMcpEvidenceSurvivesBackendProcessRestartAndExecutesOneMockAction() throws Exception {
        UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        try (var connection = database()) {
            connection.setAutoCommit(false);
            try (var user = connection.prepareStatement("INSERT INTO app_users (id, provider, provider_subject, created_at, updated_at) VALUES (?, 'GOOGLE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                user.setObject(1, owner); user.setString(2, "disposable-capstone-smoke-" + owner); user.executeUpdate();
            }
            try (var incident = connection.prepareStatement("INSERT INTO incident_investigations (id, owner_id, question, status, created_at, updated_at, start_requested) VALUES (?, ?, 'recommendation database connection pool failing', 'CREATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true)")) {
                incident.setObject(1, id); incident.setObject(2, owner); incident.executeUpdate();
            }
            connection.commit();
        }
        try {
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> status(id).equals("WAITING_APPROVAL"));
            DockerClientFactory.instance().client().restartContainerCmd("ai-incident-orchestrator-backend-1").exec();
            var http = java.net.http.HttpClient.newHttpClient();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(45)).ignoreExceptions().until(() ->
                    http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:5173/api/health"))
                            .timeout(Duration.ofSeconds(2)).build(), java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 200);
            assertThat(status(id)).isEqualTo("WAITING_APPROVAL");
            var temporal = WorkflowServiceStubs.newLocalServiceStubs();
            try {
                WorkflowClient.newInstance(temporal).newWorkflowStub(InvestigationWorkflow.class, TemporalInvestigationExecution.workflowId(id))
                        .decide(owner, ApprovalDecision.APPROVED);
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> status(id).equals("COMPLETED"));
            } finally { temporal.shutdownNow(); }
            try (var connection = database(); var query = connection.prepareStatement("SELECT evidence::text, report::text, approval, (SELECT count(*) FROM mock_remediation_actions WHERE investigation_id = ?) FROM incident_investigations WHERE id = ?")) {
                query.setObject(1, id); query.setObject(2, id);
                try (var result = query.executeQuery()) {
                    result.next(); assertThat(result.getString(1)).contains("runbook:recommendation");
                    assertThat(result.getString(2)).contains("SIMULATED", "evidenceIds");
                    assertThat(result.getString(3)).isEqualTo("APPROVED"); assertThat(result.getInt(4)).isEqualTo(1);
                }
            }
        } finally {
            try (var connection = database()) {
                connection.setAutoCommit(false);
                for (var table : java.util.List.of("mock_remediation_actions", "investigation_audit")) {
                    try (var query = connection.prepareStatement("DELETE FROM " + table + " WHERE investigation_id = ?")) { query.setObject(1, id); query.executeUpdate(); }
                }
                try (var query = connection.prepareStatement("DELETE FROM incident_investigations WHERE id = ?")) { query.setObject(1, id); query.executeUpdate(); }
                try (var query = connection.prepareStatement("DELETE FROM app_users WHERE id = ?")) { query.setObject(1, owner); query.executeUpdate(); }
                connection.commit();
            }
        }
    }
}
