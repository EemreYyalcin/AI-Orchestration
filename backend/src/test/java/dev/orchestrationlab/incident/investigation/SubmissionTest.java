package dev.orchestrationlab.incident.investigation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import dev.orchestrationlab.incident.investigation.application.*;
import dev.orchestrationlab.incident.orchestration.application.InvestigationExecution;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SubmissionTest {
    @Test void temporalOutageRetainsSavedIdAndDoesNotClaimStarted() {
        UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        var store = mock(InvestigationService.class); var execution = mock(InvestigationExecution.class);
        var limiter = mock(InvestigationStartLimiter.class); var view = mock(InvestigationService.InvestigationView.class);
        when(view.id()).thenReturn(id); when(store.create(owner, "question", true)).thenReturn(view); when(execution.durable()).thenReturn(true);
        when(execution.run(owner, id)).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        var result = new InvestigationSubmissionService(store, execution, limiter).submit(owner, "question");
        assertThat(result.pendingStart()).isTrue(); assertThat(result.investigation().id()).isEqualTo(id);
        verify(limiter).acquire(owner);
    }
    @Test void admissionFailurePreventsPersistenceAndModelWorkflowCost() {
        UUID owner = UUID.randomUUID(); var store = mock(InvestigationService.class); var execution = mock(InvestigationExecution.class);
        var limiter = mock(InvestigationStartLimiter.class);
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)).when(limiter).acquire(owner);
        assertThatThrownBy(() -> new InvestigationSubmissionService(store, execution, limiter).submit(owner, "question"))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(store, execution);
    }
}
