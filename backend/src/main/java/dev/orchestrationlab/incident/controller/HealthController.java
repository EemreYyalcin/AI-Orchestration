package dev.orchestrationlab.incident.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Basit HTTP erişilebilirlik kontrolünü sunar. Veritabanı veya dış sağlayıcıların sağlığını
 * sorgulamaz; bağımlılık kontrolü için Actuator readiness kullanılır.
 */
@RestController
public class HealthController {

    /**
     * GET /api/health için status=UP yanıtı verir; yalnızca bu HTTP endpoint’inin yanıt verebildiğini
     * gösterir.
     */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
