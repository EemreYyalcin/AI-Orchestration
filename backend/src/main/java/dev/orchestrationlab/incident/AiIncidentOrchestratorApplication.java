package dev.orchestrationlab.incident;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Backend uygulamasının Spring Boot giriş noktasıdır. Bileşen taraması, otomatik yapılandırma ve HTTP
 * sunucusunu başlatır; temporal profili etkinse aynı süreç Temporal worker görevlerini de yürütür.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AiIncidentOrchestratorApplication {

    /**
     * Komut satırı argümanlarını Spring Boot’a aktararak uygulama bağlamını ve yapılandırılmış
     * sunucuyu başlatır.
     */
    public static void main(String[] args) {
        SpringApplication.run(AiIncidentOrchestratorApplication.class, args);
    }
}
