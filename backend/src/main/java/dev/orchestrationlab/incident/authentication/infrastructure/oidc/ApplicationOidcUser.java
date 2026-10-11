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

/**
 * Spring Security OIDC kullanıcısını dahili uygulama UUID’siyle sarar. Protokol bilgileri sunucu
 * tarafındaki güvenlik principal’ında kalır; app_users tablosuna veya API profil DTO’suna yazılmaz.
 * OIDC ayrıntılarını framework nesnesine devreder.
 */
public final class ApplicationOidcUser implements OidcUser, ApplicationPrincipal, Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private final UUID userId;
    private final OidcUser delegate;

    /**
     * Null olmayan dahili UUID ve framework OIDC temsilcisini bağlar; dış subject’i dahili kimlik
     * yerine kullanmaz.
     */
    public ApplicationOidcUser(UUID userId, OidcUser delegate) {
        this.userId = Objects.requireNonNull(userId);
        this.delegate = Objects.requireNonNull(delegate);
    }

    /**
     * Uygulamanın güvendiği dahili kullanıcı UUID’sini döndürür.
     */
    @Override public UUID getUserId() { return userId; }
    /**
     * Principal adını dahili UUID’nin metin biçimi olarak verir; Google subject ile uygulama
     * kimliğinin karışmasını önler.
     */
    @Override public String getName() { return userId.toString(); }
    /**
     * OIDC temsilcisinin özniteliklerini framework için döndürür; bu nesne API profil yanıtı olarak
     * kullanılmaz.
     */
    @Override public Map<String, Object> getAttributes() { return delegate.getAttributes(); }
    /**
     * Doğrulanmış OIDC claim bilgilerine sunucu tarafında erişim sağlar.
     */
    @Override public Map<String, Object> getClaims() { return delegate.getClaims(); }
    /**
     * Framework tarafından oluşturulan yetki listesini temsilci üzerinden döndürür.
     */
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return delegate.getAuthorities(); }
    /**
     * OIDC ID token nesnesini framework için temsilciden alır; veritabanına kaydetmez.
     */
    @Override public OidcIdToken getIdToken() { return delegate.getIdToken(); }
    /**
     * Giriş akışında alınmış OIDC UserInfo nesnesini döndürür; burada yeni ağ çağrısı yapılmaz.
     */
    @Override public OidcUserInfo getUserInfo() { return delegate.getUserInfo(); }
}
