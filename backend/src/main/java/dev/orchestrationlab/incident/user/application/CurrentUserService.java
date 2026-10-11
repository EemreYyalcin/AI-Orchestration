package dev.orchestrationlab.incident.user.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.orchestrationlab.incident.user.repository.AppUserRepository;

/**
 * Dahili UUID’den kullanıcının API’ye uygun sınırlı profilini okur. Entity’yi veya protokol
 * token’larını döndürmez; salt okunur transaction kullanır.
 */
@Service
public class CurrentUserService {
    private final AppUserRepository users;

    /**
     * Kullanım senaryosunun bağımlılıklarını constructor üzerinden bağlar. Dahili UUID’den
     * kullanıcının API’ye uygun sınırlı profilini okur. Entity’yi veya protokol token’larını
     * döndürmez; salt okunur transaction kullanır.
     */
    public CurrentUserService(AppUserRepository users) { this.users = users; }

    /**
     * Kullanıcı varsa id/e-posta/görünen ad/avatar DTO’su üretir; silinmiş veya bilinmeyen UUID için
     * Optional.empty döndürür.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     */
    @Transactional(readOnly = true)
    public Optional<CurrentUser> find(UUID id) {
        return users.findById(id).map(user -> new CurrentUser(user.getId(), user.getEmail(),
                user.getDisplayName(), user.getAvatarUrl()));
    }

    /**
     * Dahili UUID ve isteğe bağlı profil alanlarını taşıyan güvenli API DTO’sudur; token, sağlayıcı
     * subject veya ham claim içermez.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param email Güncel e-posta; null olabilir ve kimlik anahtarı değildir.
     * @param displayName Güncel görünen ad; null olabilir.
     * @param avatarUrl Güncel profil görseli URL’si; null olabilir.
     */
    public record CurrentUser(UUID id, String email, String displayName, String avatarUrl) { }
}
