package dev.orchestrationlab.incident.authentication.application;

import java.security.Principal;
import java.util.UUID;

/** Application identity, independent of the login protocol. */
public interface ApplicationPrincipal extends Principal {
    UUID getUserId();
}
