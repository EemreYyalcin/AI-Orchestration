package dev.orchestrationlab.incident.authentication.infrastructure.oidc;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import dev.orchestrationlab.incident.authentication.application.ApplicationPrincipal;

/** Protocol data stays inside the server-side Security principal, never in app_users or API DTOs. */
public final class ApplicationOidcUser implements OidcUser, ApplicationPrincipal, Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private final UUID userId;
    private final OidcUser delegate;

    public ApplicationOidcUser(UUID userId, OidcUser delegate) {
        this.userId = Objects.requireNonNull(userId);
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override public UUID getUserId() { return userId; }
    @Override public String getName() { return userId.toString(); }
    @Override public Map<String, Object> getAttributes() { return delegate.getAttributes(); }
    @Override public Map<String, Object> getClaims() { return delegate.getClaims(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return delegate.getAuthorities(); }
    @Override public OidcIdToken getIdToken() { return delegate.getIdToken(); }
    @Override public OidcUserInfo getUserInfo() { return delegate.getUserInfo(); }
}
