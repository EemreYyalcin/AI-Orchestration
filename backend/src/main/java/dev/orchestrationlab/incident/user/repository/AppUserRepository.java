package dev.orchestrationlab.incident.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.orchestrationlab.incident.user.domain.AppUser;
import dev.orchestrationlab.incident.user.domain.UserProvider;

/**
 * AppUser kalıcılığının Spring Data JPA sınırıdır. Kullanıcı eşlemesi e-postayla değil
 * provider/subject çiftiyle yapılır; benzersizlik veritabanı kısıtıyla da korunur.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Sağlayıcı ve opaque subject değeri tam eşleşen kullanıcıyı bulur; bulunamazsa Optional.empty
     * verir.
     * @param provider Doğrulanmış dış kimlik sağlayıcısı.
     * @param providerSubject Sağlayıcının değişmeden saklanan opaque kullanıcı tanımlayıcısı.
     */
    Optional<AppUser> findByProviderAndProviderSubject(UserProvider provider, String providerSubject);
}
