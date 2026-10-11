package dev.orchestrationlab.incident.authentication.infrastructure.oidc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * OAuth2 access/refresh token’larının giriş sonrasında saklanmasını engelleyen repository
 * uygulamasıdır. Uygulama Google API’lerini daha sonra çağırmadığından yetkilendirilmiş istemci
 * deposuna ihtiyaç duymaz; principal’daki OIDC ID token ayrı bir konudur.
 */
public final class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {
    /**
     * Saklanmış istemci bulunmadığını null ile bildirir; access/refresh token yüklemez.
     */
    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String registrationId, Authentication principal, HttpServletRequest request) {
        return null;
    }

    /**
     * Giriş değişimi ve UserInfo okuması için kullanılan istemciyi bilerek saklamaz. Bu boş uygulama
     * access/refresh token kalıcılığını önler.
     */
    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal,
                                     HttpServletRequest request, HttpServletResponse response) {
        // Giriş değişimi ve UserInfo okumasından sonra access/refresh token’larını bilerek saklama.
    }

    /**
     * Tutulan bir istemci kaydı olmadığı için silme isteğinde işlem yapmaz.
     */
    @Override
    public void removeAuthorizedClient(String registrationId, Authentication principal,
                                       HttpServletRequest request, HttpServletResponse response) {
        // Saklanan yetkilendirilmiş istemci olmadığı için silinecek kayıt yoktur.
    }
}
