package dev.orchestrationlab.incident.investigation.controller;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import dev.orchestrationlab.incident.authentication.application.ApplicationPrincipal;
import dev.orchestrationlab.incident.investigation.application.InvestigationService;
import dev.orchestrationlab.incident.investigation.application.InvestigationService.InvestigationView;

@RestController
@RequestMapping("/api/investigations")
public class InvestigationController {
    private final InvestigationService investigations;
    private final dev.orchestrationlab.incident.investigation.application.InvestigationSubmissionService submissions;
    public InvestigationController(InvestigationService investigations, dev.orchestrationlab.incident.investigation.application.InvestigationSubmissionService submissions) {
        this.investigations = investigations; this.submissions = submissions;
    }
    @PostMapping("/{id}/run") public InvestigationView run(@AuthenticationPrincipal ApplicationPrincipal principal, @PathVariable UUID id) {
        return submissions.retry(owner(principal), id);
    }
    @PostMapping("/{id}/approve") public InvestigationView approve(@AuthenticationPrincipal ApplicationPrincipal principal, @PathVariable UUID id) {
        return submissions.decide(owner(principal), id, dev.orchestrationlab.incident.investigation.domain.ApprovalDecision.APPROVED);
    }
    @PostMapping("/{id}/deny") public InvestigationView deny(@AuthenticationPrincipal ApplicationPrincipal principal, @PathVariable UUID id) {
        return submissions.decide(owner(principal), id, dev.orchestrationlab.incident.investigation.domain.ApprovalDecision.DENIED);
    }

    @PostMapping
    public ResponseEntity<InvestigationView> create(@AuthenticationPrincipal ApplicationPrincipal principal,
                                                   @RequestBody CreateRequest request) {
        var submission = submissions.submit(owner(principal), request.question());
        var result = submission.investigation();
        return ResponseEntity.status(submission.pendingStart() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.CREATED)
                .location(URI.create("/api/investigations/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvestigationView> read(@AuthenticationPrincipal ApplicationPrincipal principal,
                                                 @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(investigations.read(owner(principal), id));
    }

    private static UUID owner(ApplicationPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return principal.getUserId();
    }
    public record CreateRequest(String question) { }
}
