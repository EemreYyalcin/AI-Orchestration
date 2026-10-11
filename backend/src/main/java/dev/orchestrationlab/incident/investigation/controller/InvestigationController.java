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

/**
 * İnceleme oluşturma/okuma/başlatma/onay/ret HTTP uçlarını sunar. Güvenilir sahip kimliğini oturum
 * principal’ından alır; iş kurallarını uygulama servislerine devreder. Durum değiştirici çağrılar
 * Spring Security CSRF korumasındadır.
 */
@RestController
@RequestMapping("/api/investigations")
public class InvestigationController {
    private final InvestigationService investigations;
    private final dev.orchestrationlab.incident.investigation.application.InvestigationSubmissionService submissions;
    /**
     * Kullanım senaryosunun bağımlılıklarını constructor üzerinden bağlar. İnceleme
     * oluşturma/okuma/başlatma/onay/ret HTTP uçlarını sunar. Güvenilir sahip kimliğini oturum
     * principal’ından alır; iş kurallarını uygulama servislerine devreder. Durum değiştirici çağrılar
     * Spring Security CSRF korumasındadır.
     */
    public InvestigationController(InvestigationService investigations, dev.orchestrationlab.incident.investigation.application.InvestigationSubmissionService submissions) {
        this.investigations = investigations; this.submissions = submissions;
    }
    /**
     * POST /{id}/run isteğini sahiplik ve kabul kontrolü yapan yeniden başlatma servisine aktarır;
     * yeni inceleme oluşturmaz.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @PostMapping("/{id}/run") public InvestigationView run(@AuthenticationPrincipal ApplicationPrincipal principal, @PathVariable UUID id) {
        return submissions.retry(owner(principal), id);
    }
    /**
     * POST /{id}/approve için APPROVED kararını dahili sahip kimliğiyle gönderir. Yürütme katmanı
     * bekleyen onayı doğrular.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @PostMapping("/{id}/approve") public InvestigationView approve(@AuthenticationPrincipal ApplicationPrincipal principal, @PathVariable UUID id) {
        return submissions.decide(owner(principal), id, dev.orchestrationlab.incident.investigation.domain.ApprovalDecision.APPROVED);
    }
    /**
     * POST /{id}/deny için DENIED kararı gönderir; geçerli ret incelemeyi mock eylem oluşturmadan
     * tamamlatır.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @PostMapping("/{id}/deny") public InvestigationView deny(@AuthenticationPrincipal ApplicationPrincipal principal, @PathVariable UUID id) {
        return submissions.decide(owner(principal), id, dev.orchestrationlab.incident.investigation.domain.ApprovalDecision.DENIED);
    }

    /**
     * Soru gövdesinden inceleme oluşturur. Normal kabulde 201, kaydedilip kalıcı başlatması bekleyen
     * durumda 503 verir; iki durumda da görünüm, kimliği gösteren Location ve no-store başlığı döner.
     */
    @PostMapping
    public ResponseEntity<InvestigationView> create(@AuthenticationPrincipal ApplicationPrincipal principal,
                                                   @RequestBody CreateRequest request) {
        var submission = submissions.submit(owner(principal), request.question());
        var result = submission.investigation();
        return ResponseEntity.status(submission.pendingStart() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.CREATED)
                .location(URI.create("/api/investigations/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    /**
     * GET /{id} için yalnızca oturum sahibinin inceleme görünümünü no-store başlığıyla döndürür.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @GetMapping("/{id}")
    public ResponseEntity<InvestigationView> read(@AuthenticationPrincipal ApplicationPrincipal principal,
                                                 @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(investigations.read(owner(principal), id));
    }

    /**
     * Principal yoksa 401 üretir; varsa dahili UUID’yi çıkarır. Kullanıcı gövdesinden veya model
     * çıktısından sahip kimliği alınmaz.
     */
    private static UUID owner(ApplicationPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return principal.getUserId();
    }
    /**
     * İnceleme oluşturma isteğinin yalnızca soru içeren DTO’sudur; sahip kimliği oturumdan alınır.
     * @param question Kullanıcının olay sorusu; kalıcı oluşturma sınırı 4000 karakterdir.
     */
    public record CreateRequest(String question) { }
}
