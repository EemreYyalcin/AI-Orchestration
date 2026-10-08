package dev.orchestrationlab.incident.user.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.orchestrationlab.incident.user.repository.AppUserRepository;

@Service
public class CurrentUserService {
    private final AppUserRepository users;

    public CurrentUserService(AppUserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public Optional<CurrentUser> find(UUID id) {
        return users.findById(id).map(user -> new CurrentUser(user.getId(), user.getEmail(),
                user.getDisplayName(), user.getAvatarUrl()));
    }

    public record CurrentUser(UUID id, String email, String displayName, String avatarUrl) { }
}
