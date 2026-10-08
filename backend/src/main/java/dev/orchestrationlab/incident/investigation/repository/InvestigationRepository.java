package dev.orchestrationlab.incident.investigation.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.orchestrationlab.incident.investigation.domain.IncidentInvestigation;

public interface InvestigationRepository extends JpaRepository<IncidentInvestigation, UUID> {
    Optional<IncidentInvestigation> findByIdAndOwnerId(UUID id, UUID ownerId);
}
