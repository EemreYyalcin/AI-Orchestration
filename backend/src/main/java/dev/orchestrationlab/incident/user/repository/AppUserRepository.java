package dev.orchestrationlab.incident.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.orchestrationlab.incident.user.domain.AppUser;
import dev.orchestrationlab.incident.user.domain.UserProvider;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByProviderAndProviderSubject(UserProvider provider, String providerSubject);
}
