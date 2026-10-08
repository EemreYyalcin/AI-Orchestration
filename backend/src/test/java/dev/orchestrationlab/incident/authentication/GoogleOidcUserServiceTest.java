package dev.orchestrationlab.incident.authentication;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import dev.orchestrationlab.incident.authentication.infrastructure.oidc.ApplicationOidcUser;
import dev.orchestrationlab.incident.authentication.infrastructure.oidc.GoogleOidcUserService;
import dev.orchestrationlab.incident.user.application.ExternalIdentity;
import dev.orchestrationlab.incident.user.application.UserProvisioningService;
import dev.orchestrationlab.incident.user.domain.AppUser;
import dev.orchestrationlab.incident.user.domain.UserProvider;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleOidcUserServiceTest {
    @Test
    void mapsValidatedClaimsAndKeepsInternalUuidSeparateFromSubject() {
        var provisioning = mock(UserProvisioningService.class);
        var user = mock(AppUser.class);
        UUID id = UUID.randomUUID();
        when(user.getId()).thenReturn(id);
        when(provisioning.provision(any())).thenReturn(user);
        var claims = Map.<String, Object>of("sub", "opaque-google-subject", "email", "same@example.com",
                "name", "Example User", "picture", "https://example.com/avatar.png");
        var token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(60), claims);
        var oidc = new DefaultOidcUser(Set.of(), token);
        var adapter = new GoogleOidcUserService(request -> oidc, provisioning);

        var principal = (ApplicationOidcUser) adapter.loadUser(request(token));

        var identity = ArgumentCaptor.forClass(ExternalIdentity.class);
        verify(provisioning).provision(identity.capture());
        assertThat(identity.getValue()).isEqualTo(new ExternalIdentity(UserProvider.GOOGLE,
                "opaque-google-subject", "same@example.com", "Example User", "https://example.com/avatar.png"));
        assertThat(principal.getUserId()).isEqualTo(id);
        assertThat(principal.getName()).isEqualTo(id.toString());
        assertThat(principal.getSubject()).isEqualTo("opaque-google-subject");
    }

    @Test
    void databaseFailureIsAuthenticationFailure() {
        var provisioning = mock(UserProvisioningService.class);
        when(provisioning.provision(any())).thenThrow(new IllegalStateException("database unavailable"));
        var token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "subject"));
        var adapter = new GoogleOidcUserService(request -> new DefaultOidcUser(Set.of(), token), provisioning);
        assertThatThrownBy(() -> adapter.loadUser(request(token)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessage("Unable to complete sign in");
    }

    private static OidcUserRequest request(OidcIdToken token) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId("test-client").clientSecret("test-secret").build();
        var access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "test-access-token",
                Instant.now(), Instant.now().plusSeconds(60));
        return new OidcUserRequest(google, access, token);
    }
}
