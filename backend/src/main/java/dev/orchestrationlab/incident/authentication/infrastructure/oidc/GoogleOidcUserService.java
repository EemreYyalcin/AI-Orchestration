package dev.orchestrationlab.incident.authentication.infrastructure.oidc;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import dev.orchestrationlab.incident.user.application.ExternalIdentity;
import dev.orchestrationlab.incident.user.application.UserProvisioningService;
import dev.orchestrationlab.incident.user.domain.UserProvider;

/**
 * Spring Security’nin OIDC kullanıcı yükleme işlemini uygulama kullanıcı kaydıyla birleştirir.
 * Yalnızca google kaydını kabul eder; protokol işlerini framework’e, profil kalıcılığını
 * UserProvisioningService’e bırakır.
 */
public final class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final UserProvisioningService provisioning;

    /**
     * Framework kullanıcı yükleyicisini ve uygulama kullanıcı kayıt servisini bağlar; protokol ve
     * kalıcılık sorumluluklarını ayrı tutar.
     */
    public GoogleOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate,
                                 UserProvisioningService provisioning) {
        this.delegate = delegate;
        this.provisioning = provisioning;
    }

    /**
     * Önce framework üzerinden OIDC kullanıcısını ve gerekiyorsa UserInfo yanıtını alır, sonra
     * güvenilir provider/subject ve profil alanlarıyla kullanıcıyı oluşturur veya günceller. Sonucu
     * dahili UUID’li principal ile sarar. Kayıt başarısızsa kimlik doğrulama da başarısız olur;
     * kullanıcı kaydı öncesinde doğrulanmış oturum kurulmaz.
     */
    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        if (!"google".equals(request.getClientRegistration().getRegistrationId())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("unsupported_provider"));
        }
        // Framework token doğrulaması ve UserInfo ağ işi veritabanı transaction’ından önce tamamlanır.
        OidcUser oidcUser = delegate.loadUser(request);
        try {
            var identity = new ExternalIdentity(UserProvider.GOOGLE, oidcUser.getSubject(),
                    oidcUser.getEmail(), oidcUser.getFullName(), oidcUser.getPicture());
            var user = provisioning.provision(identity);
            return new ApplicationOidcUser(user.getId(), oidcUser);
        } catch (RuntimeException failure) {
            // Bu noktada giriş henüz başarılı değildir ve doğrulanmış SecurityContext kaydedilmemiştir.
            throw new OAuth2AuthenticationException(new OAuth2Error("user_provisioning_failed"),
                    "Unable to complete sign in", failure);
        }
    }
}
