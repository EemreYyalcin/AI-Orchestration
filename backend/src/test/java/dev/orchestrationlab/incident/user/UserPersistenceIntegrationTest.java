package dev.orchestrationlab.incident.user;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import dev.orchestrationlab.incident.user.application.UserProvisioningService;
import dev.orchestrationlab.incident.user.application.ExternalIdentity;
import dev.orchestrationlab.incident.user.domain.AppUser;
import dev.orchestrationlab.incident.user.domain.UserProvider;
import dev.orchestrationlab.incident.user.repository.AppUserRepository;
import dev.orchestrationlab.incident.authentication.infrastructure.oidc.ApplicationOidcUser;
import dev.orchestrationlab.incident.authentication.infrastructure.oidc.GoogleOidcUserService;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(UserPersistenceIntegrationTest.PostgresConfiguration.class)
class UserPersistenceIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfiguration {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18");
        }
    }

    @MockitoSpyBean AppUserRepository users;
    @Autowired UserProvisioningService provisioning;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void clearUsers() {
        users.deleteAllInBatch();
    }

    @Test
    void oidcAdapterUsesExistingProvisioningAgainstPostgres() {
        var token = new OidcIdToken(
                "test-only-id-token", Instant.now(), Instant.now().plusSeconds(60),
                java.util.Map.of("sub", "adapter-subject", "email", "adapter@example.com", "name", "First name"));
        var oidc = new DefaultOidcUser(java.util.Set.of(), token);
        var adapter = new GoogleOidcUserService(request -> oidc, provisioning);
        var registration = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId("test-only-client").clientSecret("test-only-secret").build();
        var access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "test-only-access",
                Instant.now(), Instant.now().plusSeconds(60));
        var request = new OidcUserRequest(registration, access, token);
        var first = (ApplicationOidcUser) adapter.loadUser(request);
        var returning = (ApplicationOidcUser) adapter.loadUser(request);
        assertThat(first.getUserId()).isEqualTo(returning.getUserId());
        assertThat(users.count()).isEqualTo(1);
        var stored = users.findById(first.getUserId()).orElseThrow();
        assertThat(stored.getProviderSubject()).isEqualTo("adapter-subject");
        assertThat(stored.getEmail()).isEqualTo("adapter@example.com");
    }

    @Test
    void flywayOwnsSchemaAndHibernateValidatesIt() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'app_users' AND column_name = 'id'
                """, String.class)).isEqualTo("uuid");
        assertThat(jdbc.queryForList("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'app_users'
                  AND column_name IN ('created_at', 'updated_at')
                """, String.class)).containsExactlyInAnyOrder(
                        "timestamp with time zone", "timestamp with time zone");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE conrelid = 'app_users'::regclass AND contype = 'u'
                  AND conname = 'uk_app_users_provider_subject'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void provisionsAndReloadsUserWithIndependentUuidAndOptionalProfile() {
        AppUser created = provision(UserProvider.GOOGLE, "google-subject", null, null, null);
        assertThat(created.getId()).isNotNull();

        AppUser loaded = users.findByProviderAndProviderSubject(UserProvider.GOOGLE, "google-subject")
                .orElseThrow();
        assertThat(loaded.getId()).isEqualTo(created.getId());
        assertThat(loaded.getProvider()).isEqualTo(UserProvider.GOOGLE);
        assertThat(loaded.getProviderSubject()).isEqualTo("google-subject");
        assertThat(loaded.getEmail()).isNull();
        assertThat(loaded.getDisplayName()).isNull();
        assertThat(loaded.getAvatarUrl()).isNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
        assertThat(users.findByProviderAndProviderSubject(UserProvider.GOOGLE, "missing")).isEmpty();
    }

    @Test
    void repeatedProvisioningUpdatesProfileAndKeepsIdentityAndCreationTime() {
        AppUser original = provision(UserProvider.GOOGLE, "returning-subject",
                "old@example.test", "Old name", "https://example.test/old.png");
        Instant oldTimestamp = Instant.parse("2000-01-01T00:00:00Z");
        jdbc.update("UPDATE app_users SET created_at = ?, updated_at = ? WHERE id = ?",
                OffsetDateTime.parse("2000-01-01T00:00:00Z"),
                OffsetDateTime.parse("2000-01-01T00:00:00Z"), original.getId());

        AppUser updated = provision(UserProvider.GOOGLE, "returning-subject",
                "new@example.test", "New name", "https://example.test/new.png");
        AppUser loaded = users.findById(updated.getId()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(original.getId());
        assertThat(loaded.getProviderSubject()).isEqualTo("returning-subject");
        assertThat(loaded.getEmail()).isEqualTo("new@example.test");
        assertThat(loaded.getDisplayName()).isEqualTo("New name");
        assertThat(loaded.getAvatarUrl()).isEqualTo("https://example.test/new.png");
        assertThat(loaded.getCreatedAt()).isEqualTo(oldTimestamp);
        assertThat(loaded.getUpdatedAt()).isAfter(oldTimestamp);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void identicalProfileDoesNotRewriteTimestampsAndNullSnapshotClearsProfile() {
        AppUser created = provision(UserProvider.GOOGLE, "unchanged-subject",
                "same@example.test", "Same name", "https://example.test/avatar.png");
        AppUser before = users.findById(created.getId()).orElseThrow();
        provision(UserProvider.GOOGLE, "unchanged-subject",
                "same@example.test", "Same name", "https://example.test/avatar.png");
        AppUser unchanged = users.findById(created.getId()).orElseThrow();
        assertThat(unchanged.getUpdatedAt()).isEqualTo(before.getUpdatedAt());

        provision(UserProvider.GOOGLE, "unchanged-subject", null, null, null);
        AppUser cleared = users.findById(created.getId()).orElseThrow();
        assertThat(cleared.getEmail()).isNull();
        assertThat(cleared.getDisplayName()).isNull();
        assertThat(cleared.getAvatarUrl()).isNull();
    }

    @Test
    void differentSubjectsCanShareEmailAndOpaqueSubjectsArePreserved() {
        String email = "shared@example.test";
        AppUser first = provision(UserProvider.GOOGLE, "Subject", email, null, null);
        AppUser second = provision(UserProvider.GOOGLE, "subject", email, null, null);
        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(users.count()).isEqualTo(2);
        assertThat(users.findByProviderAndProviderSubject(UserProvider.GOOGLE, "Subject")
                .orElseThrow().getProviderSubject()).isEqualTo("Subject");
    }

    @Test
    void postgresRejectsDuplicateIdentityWithoutLeavingExtraRows() {
        provision(UserProvider.GOOGLE, "duplicate", null, null, null);
        assertThatThrownBy(() -> users.saveAndFlush(
                new AppUser(UserProvider.GOOGLE, "duplicate", null, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void postgresRejectsNullIdentityFieldsEvenWhenJpaIsBypassed() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO app_users (id, provider, provider_subject, created_at, updated_at)
                VALUES (?, NULL, 'subject', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO app_users (id, provider, provider_subject, created_at, updated_at)
                VALUES (?, 'GOOGLE', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.count()).isZero();
    }

    @Test
    void failedProfileUpdateRollsBackAndDoesNotRetryUnrelatedConstraintFailures() {
        AppUser original = provision(UserProvider.GOOGLE, "rollback-subject", "good@example.test", "Before", null);
        jdbc.execute("""
                ALTER TABLE app_users ADD CONSTRAINT ck_test_profile
                CHECK (email IS DISTINCT FROM 'rejected@example.test')
                """);
        try {
            assertThatThrownBy(() -> provision(UserProvider.GOOGLE, "rollback-subject",
                    "rejected@example.test", "After", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            AppUser loaded = users.findById(original.getId()).orElseThrow();
            assertThat(loaded.getEmail()).isEqualTo("good@example.test");
            assertThat(loaded.getDisplayName()).isEqualTo("Before");
            verify(users, times(2)).findByProviderAndProviderSubject(UserProvider.GOOGLE, "rollback-subject");
        } finally {
            jdbc.execute("ALTER TABLE app_users DROP CONSTRAINT ck_test_profile");
        }
    }

    @Test
    void concurrentInsertsCannotCreateTwoUsersForOneIdentity() throws Exception {
        Callable<Boolean> insert = () -> {
            try {
                users.saveAndFlush(new AppUser(UserProvider.GOOGLE, "concurrent", null, null, null));
                return true;
            } catch (DataIntegrityViolationException expected) {
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(insert);
            var second = executor.submit(insert);
            boolean firstSucceeded = first.get(20, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(20, TimeUnit.SECONDS);
            assertThat(firstSucceeded ^ secondSucceeded).isTrue();
        }
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void repeatedIdentityConflictStopsAfterTwoAttempts() {
        provision(UserProvider.GOOGLE, "bounded-retry", null, null, null);
        // Simulate the row remaining invisible to both lookups, but let PostgreSQL
        // execute and reject both inserts. This exercises the actual constraint path.
        doReturn(Optional.empty()).when(users)
                .findByProviderAndProviderSubject(UserProvider.GOOGLE, "bounded-retry");
        assertThatThrownBy(() -> provision(UserProvider.GOOGLE, "bounded-retry", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // One lookup for initial creation, then exactly two failed provisioning attempts.
        verify(users, times(3)).findByProviderAndProviderSubject(UserProvider.GOOGLE, "bounded-retry");
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void concurrentFirstProvisioningRetriesOnceAndReturnsTheSameUser() throws Exception {
        CountDownLatch bothLookedUp = new CountDownLatch(2);
        AtomicInteger lookups = new AtomicInteger();
        // The spy only coordinates timing; every lookup/insert still hits PostgreSQL.
        doAnswer(invocation -> {
            // Spring Data's finder is an interface method; delegate via the spy's
            // original answer instead of trying to invoke an abstract real method.
            Object result = mockingDetails(users).getMockCreationSettings().getDefaultAnswer().answer(invocation);
            if (lookups.incrementAndGet() <= 2) {
                bothLookedUp.countDown();
                if (!bothLookedUp.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent lookup did not arrive");
                }
            }
            return result;
        }).when(users).findByProviderAndProviderSubject(UserProvider.GOOGLE, "racing-subject");

        Callable<AppUser> provision = () -> provision(UserProvider.GOOGLE, "racing-subject",
                "race@example.test", "Concurrent user", null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(provision);
            var second = executor.submit(provision);
            AppUser firstUser = first.get(20, TimeUnit.SECONDS);
            AppUser secondUser = second.get(20, TimeUnit.SECONDS);
            assertThat(firstUser.getId()).isEqualTo(secondUser.getId());
        }
        assertThat(lookups.get()).isEqualTo(3);
        assertThat(users.count()).isEqualTo(1);
    }

    private AppUser provision(UserProvider provider, String providerSubject,
                              String email, String displayName, String avatarUrl) {
        return provisioning.provision(new ExternalIdentity(provider, providerSubject, email, displayName, avatarUrl));
    }
}
