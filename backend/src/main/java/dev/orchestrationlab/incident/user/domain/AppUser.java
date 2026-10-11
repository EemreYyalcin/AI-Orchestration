package dev.orchestrationlab.incident.user.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * app_users tablosundaki uygulama kullanıcısıdır. Dahili UUID, provider/subject kimliğinden
 * bağımsızdır; provider ve subject oluşturma sonrasında değişmez. E-posta/ad/avatar güncellenebilir
 * profil alanlarıdır; OAuth token veya ham claim saklanmaz.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private UserProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255, updatable = false)
    private String providerSubject;

    @Column(columnDefinition = "text")
    private String email;

    @Column(name = "display_name", columnDefinition = "text")
    private String displayName;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * JPA’nın entity’yi veritabanından oluşturabilmesi için gereken boş constructor’dır; uygulama yeni
     * kayıt için doğrulamalı constructor’ı kullanır.
     */
    protected AppUser() {
        // JPA’nın veritabanından entity oluşturması için gereklidir.
    }

    /**
     * Sağlayıcı/subject kimliğini doğrular, opaque subject’i aynen korur ve güncel profil alanlarını
     * atar. Dahili UUID ilk kalıcılaştırmada üretilir.
     */
    public AppUser(UserProvider provider, String providerSubject,
                   String email, String displayName, String avatarUrl) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        if (providerSubject == null || providerSubject.isBlank() || providerSubject.length() > 255) {
            throw new IllegalArgumentException("providerSubject must contain 1–255 characters and not be blank");
        }
        // Sağlayıcı subject’i opaque kimliktir; tam değeri değiştirmeden korunur.
        this.providerSubject = providerSubject;
        updateProfile(email, displayName, avatarUrl);
    }

    /**
     * Güncel e-posta, görünen ad ve avatarı aynen yazar; null değer önceki alanı temizler. Kimlik
     * anahtarlarını değiştirmez.
     * @param email Güncel e-posta; null olabilir ve kimlik anahtarı değildir.
     * @param displayName Güncel görünen ad; null olabilir.
     * @param avatarUrl Güncel profil görseli URL’si; null olabilir.
     */
    public void updateProfile(String email, String displayName, String avatarUrl) {
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    /**
     * İlk JPA kaydında oluşturulma ve güncellenme zamanını aynı UTC anlamlı Instant olarak atar.
     */
    @PrePersist
    private void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * JPA güncellemesi öncesinde yalnızca son güncelleme zamanını yeniler.
     */
    @PreUpdate
    private void updateTimestamp() {
        updatedAt = Instant.now();
    }

    /**
     * Veritabanının oluşturduğu dahili kayıt UUID’sini döndürür.
     */
    public UUID getId() { return id; }
    /**
     * Kullanıcının değişmez dış kimlik sağlayıcısını döndürür.
     */
    public UserProvider getProvider() { return provider; }
    /**
     * Sağlayıcının opaque kullanıcı tanımlayıcısını aynen döndürür; e-posta veya dahili UUID değildir.
     */
    public String getProviderSubject() { return providerSubject; }
    /**
     * Son profil anlık görüntüsündeki e-postayı döndürür; null olabilir ve kimlik anahtarı değildir.
     */
    public String getEmail() { return email; }
    /**
     * Son profildeki görünen adı döndürür; sağlayıcı vermediyse null olabilir.
     */
    public String getDisplayName() { return displayName; }
    /**
     * Son profildeki avatar URL’sini döndürür; tarayıcı HTTPS kontrolünü ayrıca yapar.
     */
    public String getAvatarUrl() { return avatarUrl; }
    /**
     * Kaydın ilk oluşturulma zamanını döndürür; güncellemelerde korunur.
     */
    public Instant getCreatedAt() { return createdAt; }
    /**
     * Kaydın JPA tarafından son güncellenme zamanını döndürür.
     */
    public Instant getUpdatedAt() { return updatedAt; }
}
