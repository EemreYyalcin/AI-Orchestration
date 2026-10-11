package dev.orchestrationlab.incident.investigation.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.orchestrationlab.incident.investigation.domain.IncidentInvestigation;

/**
 * İnceleme entity’leri için Spring Data JPA kalıcılık sınırıdır. HTTP ve araç katmanlarının kullandığı
 * sahiplik kapsamlı arama, yabancı kullanıcının kaydına erişimi engeller.
 */
public interface InvestigationRepository extends JpaRepository<IncidentInvestigation, UUID> {
    /**
     * Yalnızca id ve ownerId birlikte eşleşen incelemeyi döndürür; bulunamayan veya yabancı kayıt
     * Optional.empty olur.
     * @param id İşlem yapılan dahili kayıt veya kanıt kimliği.
     * @param ownerId İncelemenin veya oturumun dahili kullanıcı UUID’si.
     */
    Optional<IncidentInvestigation> findByIdAndOwnerId(UUID id, UUID ownerId);
}
