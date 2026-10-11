package dev.orchestrationlab.incident.user.application;

import java.util.Objects;

import dev.orchestrationlab.incident.user.domain.UserProvider;

/**
 * Doğrulanmış sağlayıcı kimliği ve güncel profil anlık görüntüsüdür; giriş kimlik bilgisi veya token
 * taşımaz. provider ve opaque providerSubject kimlik anahtarıdır; null profil alanları eski değerleri
 * temizleyebilir.
 * @param provider Doğrulanmış dış kimlik sağlayıcısı.
 * @param providerSubject Sağlayıcının değişmeden saklanan opaque kullanıcı tanımlayıcısı.
 * @param email Güncel e-posta; null olabilir ve kimlik anahtarı değildir.
 * @param displayName Güncel görünen ad; null olabilir.
 * @param avatarUrl Güncel profil görseli URL’si; null olabilir.
 */
public record ExternalIdentity(UserProvider provider, String providerSubject,
                               String email, String displayName, String avatarUrl) {

    /**
     * Sağlayıcıyı ve 1–255 karakterlik, boş olmayan subject’i doğrular. İsteğe bağlı profil alanları
     * null olabilir; opaque subject aynen korunur.
     */
    public ExternalIdentity {
        Objects.requireNonNull(provider, "provider must not be null");
        if (providerSubject == null || providerSubject.isBlank() || providerSubject.length() > 255) {
            throw new IllegalArgumentException("providerSubject must contain 1–255 characters and not be blank");
        }
    }
}
