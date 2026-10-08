package dev.orchestrationlab.incident.user.application;

import java.util.Objects;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import dev.orchestrationlab.incident.user.domain.AppUser;
import dev.orchestrationlab.incident.user.repository.AppUserRepository;

@Service
public class UserProvisioningService {

    private final AppUserRepository users;
    private final TransactionTemplate transaction;

    public UserProvisioningService(AppUserRepository users, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.transaction = new TransactionTemplate(transactionManager);
        // Every attempt must be independent of a caller's transaction. A PostgreSQL
        // constraint failure aborts its transaction; recovery needs a fresh one.
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Persists an already trusted identity and its current profile snapshot.
     * This service does not authenticate or validate OIDC tokens.
     * Null profile fields clear previously stored values.
     */
    public AppUser provision(ExternalIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        try {
            return executeAttempt(identity);
        } catch (DataIntegrityViolationException failure) {
            if (!isIdentityConflict(failure)) {
                throw failure;
            }
            // The first transaction has rolled back. Retry once: normally this
            // reloads the winner's row. Any second failure propagates to the caller.
            return executeAttempt(identity);
        }
    }

    private AppUser executeAttempt(ExternalIdentity identity) {
        return Objects.requireNonNull(transaction.execute(status -> provisionInTransaction(identity)));
    }

    private AppUser provisionInTransaction(ExternalIdentity identity) {
        return users.findByProviderAndProviderSubject(identity.provider(), identity.providerSubject())
                .map(existing -> {
                    existing.updateProfile(identity.email(), identity.displayName(), identity.avatarUrl());
                    // Flush within this transaction so callbacks and constraints run here.
                    users.flush();
                    return existing;
                })
                .orElseGet(() -> users.saveAndFlush(new AppUser(identity.provider(), identity.providerSubject(),
                        identity.email(), identity.displayName(), identity.avatarUrl())));
    }

    private boolean isIdentityConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "uk_app_users_provider_subject".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
