package dev.orchestrationlab.incident.authentication.infrastructure.oidc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/** Login needs protocol tokens temporarily; this application does not call Google APIs afterward. */
public final class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {
    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String registrationId, Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal,
                                     HttpServletRequest request, HttpServletResponse response) {
        // Deliberately discard access/refresh tokens after the login exchange and UserInfo loading.
    }

    @Override
    public void removeAuthorizedClient(String registrationId, Authentication principal,
                                       HttpServletRequest request, HttpServletResponse response) {
        // No retained authorized client to remove.
    }
}
