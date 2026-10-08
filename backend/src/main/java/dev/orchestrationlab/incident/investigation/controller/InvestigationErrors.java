package dev.orchestrationlab.incident.investigation.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import dev.orchestrationlab.incident.investigation.application.InvestigationService;

@RestControllerAdvice(basePackageClasses = InvestigationController.class)
public class InvestigationErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalid() { return error(HttpStatus.BAD_REQUEST, "Invalid investigation request"); }
    @ExceptionHandler(InvestigationService.NotFound.class)
    ResponseEntity<ProblemDetail> absent() { return error(HttpStatus.NOT_FOUND, "Investigation not found"); }
    @ExceptionHandler(InvestigationService.MissingOwner.class)
    ResponseEntity<ProblemDetail> staleSession() { return error(HttpStatus.UNAUTHORIZED, "Authentication required"); }
    @ExceptionHandler({IllegalStateException.class, org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> conflict() { return error(HttpStatus.CONFLICT, "Investigation state changed; refresh before retrying"); }
    private ResponseEntity<ProblemDetail> error(HttpStatus status, String detail) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
