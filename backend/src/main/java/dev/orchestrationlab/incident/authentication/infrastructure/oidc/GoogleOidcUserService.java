package dev.orchestrationlab.incident.authentication.infrastructure.oidc;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import dev.orchestrationlab.incident.user.application.ExternalIdentity;
import dev.orchestrationlab.incident.user.application.UserProvisioningService;
import dev.orchestrationlab.incident.user.domain.UserProvider;

public final class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final UserProvisioningService provisioning;

    public GoogleOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate,
                                 UserProvisioningService provisioning) {
        this.delegate = delegate;
        this.provisioning = provisioning;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        if (!"google".equals(request.getClientRegistration().getRegistrationId())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("unsupported_provider"));
        }
        // Framework token validation and any UserInfo network request precede the database transaction.
        OidcUser oidcUser = delegate.loadUser(request);
        try {
            var identity = new ExternalIdentity(UserProvider.GOOGLE, oidcUser.getSubject(),
                    oidcUser.getEmail(), oidcUser.getFullName(), oidcUser.getPicture());
            var user = provisioning.provision(identity);
            return new ApplicationOidcUser(user.getId(), oidcUser);
        } catch (RuntimeException failure) {
            // Authentication has not succeeded or saved a SecurityContext at this point.
            throw new OAuth2AuthenticationException(new OAuth2Error("user_provisioning_failed"),
                    "Unable to complete sign in", failure);
        }
    }
}
