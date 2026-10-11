package dev.orchestrationlab.incident.authentication.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tarayıcıya oturuma bağlı CSRF bilgisini sunar. CSRF, tarayıcının çerezle yaptığı durum değiştirici
 * isteklerin uygulama arayüzünden geldiğini denetlemeye yardımcı olur; kullanıcı kimliği doğrulaması
 * sağlamaz.
 */
@RestController
public class CsrfController {

    /**
     * GET /api/csrf için token ve gönderileceği HTTP başlığının adını döndürür. no-store, oturuma özel
     * yanıtın önbellekte tutulmasını önler; POST işlemleri bu başlığı kullanır.
     */
    @GetMapping("/api/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getToken(), token.getHeaderName()));
    }

    /**
     * CSRF token değerini ve POST isteğinde kullanılacak başlık adını taşıyan yanıt DTO’sudur. Token
     * kullanıcı kimliği yerine geçmez.
     */
    public record CsrfResponse(String token, String headerName) { }
}
