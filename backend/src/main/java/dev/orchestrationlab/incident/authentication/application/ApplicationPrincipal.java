package dev.orchestrationlab.incident.authentication.application;

import java.security.Principal;
import java.util.UUID;

/**
 * Giriş sağlayıcısından bağımsız, uygulamanın güvendiği kullanıcı kimliği sözleşmesidir. Sahiplik
 * kontrolleri Google subject veya e-posta yerine bu arayüzün dahili UUID değerini kullanır.
 */
public interface ApplicationPrincipal extends Principal {
    /**
     * Doğrulanmış oturumun uygulama içindeki kullanıcı UUID değerini döndürür; bu değer inceleme
     * sahipliğinin anahtarıdır.
     */
    UUID getUserId();
}
