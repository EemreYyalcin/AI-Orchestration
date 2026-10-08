package dev.orchestrationlab.incident.reasoning;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.mock.env.MockEnvironment;
import dev.orchestrationlab.incident.observability.InvestigationTelemetry;
import dev.orchestrationlab.incident.tool.application.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class TelemetryTest {
    @Test void boundedMetricsRecordTimeoutAndApprovalWithoutUserLabels() {
        var meters = new SimpleMeterRegistry(); var telemetry = new InvestigationTelemetry(meters, ObservationRegistry.create(), new MockEnvironment());
        telemetry.tool(ToolResult.failed(ToolKind.LOGS, ToolResult.Failure.TIMEOUT), 1000);
        telemetry.approval(Duration.ofSeconds(5), "DENIED"); telemetry.completed("success", Duration.ofSeconds(6));
        assertThat(meters.get("incident.timeouts").counter().count()).isEqualTo(1);
        assertThat(meters.get("incident.approval.wait").timer().count()).isEqualTo(1);
        assertThat(meters.getMeters().stream().flatMap(m -> m.getId().getTags().stream()).map(t -> t.getKey()).toList())
                .doesNotContain("investigation.id", "question", "owner.id");
    }
}
