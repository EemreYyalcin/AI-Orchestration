package dev.orchestrationlab.incident.investigation.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import dev.orchestrationlab.incident.investigation.application.InvestigationService;

/**
 * İnceleme controller’ındaki bilinen girdi, sahiplik ve durum hatalarını güvenli ProblemDetail HTTP
 * yanıtlarına dönüştürür. İç exception ayrıntılarını ve veritabanı bilgisini dışarıya taşımaz.
 */
@RestControllerAdvice(basePackageClasses = InvestigationController.class)
public class InvestigationErrors {
    /**
     * Geçersiz soru veya tipli girdi için genel 400 yanıtı üretir.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalid() { return error(HttpStatus.BAD_REQUEST, "Invalid investigation request"); }
    /**
     * Bulunamayan veya başka kullanıcıya ait incelemeyi aynı genel 404 yanıtıyla gizler.
     */
    @ExceptionHandler(InvestigationService.NotFound.class)
    ResponseEntity<ProblemDetail> absent() { return error(HttpStatus.NOT_FOUND, "Investigation not found"); }
    /**
     * Principal’ın işaret ettiği kullanıcı kalmamışsa yeniden kimlik doğrulama isteyen 401 yanıtı
     * üretir.
     */
    @ExceptionHandler(InvestigationService.MissingOwner.class)
    ResponseEntity<ProblemDetail> staleSession() { return error(HttpStatus.UNAUTHORIZED, "Authentication required"); }
    /**
     * Geçersiz durum geçişi veya optimistic lock çakışmasını 409’a çevirir; istemcinin güncel durumu
     * okuması gerekir.
     */
    @ExceptionHandler({IllegalStateException.class, org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> conflict() { return error(HttpStatus.CONFLICT, "Investigation state changed; refresh before retrying"); }
    /**
     * Verilen HTTP durumu ve güvenli açıklamadan önbelleğe alınmayan ProblemDetail yanıtı oluşturur.
     */
    private ResponseEntity<ProblemDetail> error(HttpStatus status, String detail) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
