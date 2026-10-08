package dev.orchestrationlab.incident.user.application;

import java.util.Objects;

import dev.orchestrationlab.incident.user.domain.UserProvider;

/** A trusted provider identity and current profile snapshot, not authentication credentials. */
public record ExternalIdentity(UserProvider provider, String providerSubject,
                               String email, String displayName, String avatarUrl) {

    public ExternalIdentity {
        Objects.requireNonNull(provider, "provider must not be null");
        if (providerSubject == null || providerSubject.isBlank() || providerSubject.length() > 255) {
            throw new IllegalArgumentException("providerSubject must contain 1–255 characters and not be blank");
        }
    }
}
