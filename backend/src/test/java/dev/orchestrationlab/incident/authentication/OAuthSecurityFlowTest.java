package dev.orchestrationlab.incident.authentication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.orchestrationlab.incident.authentication.controller.CurrentUserController;
import dev.orchestrationlab.incident.authentication.controller.CsrfController;
import dev.orchestrationlab.incident.configuration.SecurityConfiguration;
import dev.orchestrationlab.incident.user.application.CurrentUserService;
import dev.orchestrationlab.incident.user.application.CurrentUserService.CurrentUser;
import dev.orchestrationlab.incident.user.application.ExternalIdentity;
import dev.orchestrationlab.incident.user.application.UserProvisioningService;
import dev.orchestrationlab.incident.user.domain.AppUser;
import dev.orchestrationlab.incident.user.domain.UserProvider;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Loopback OIDC provider with signed tokens: no real credentials or Google network calls. */
@WebMvcTest(controllers = {CurrentUserController.class, CsrfController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfiguration.class)
@ActiveProfiles("google")
class OAuthSecurityFlowTest {
    private static final String SUBJECT = "test-google-subject";
    private static final String CLIENT = "test-only-client";
    private static final RSAKey KEY = key();
    private static volatile String nonce;
    private static volatile boolean invalidSignature;
    private static final AtomicInteger tokenCalls = new AtomicInteger();
    private static final HttpServer PROVIDER = provider();

    @Autowired MockMvc mvc;
    @Autowired OAuth2AuthorizedClientService authorizedClients;
    @MockitoBean UserProvisioningService provisioning;
    @MockitoBean CurrentUserService currentUsers;

    @DynamicPropertySource
    static void registration(DynamicPropertyRegistry properties) {
        // Exercise application-google.yml itself; credentials and provider endpoints are fixtures only.
        properties.add("GOOGLE_CLIENT_ID", () -> CLIENT);
        properties.add("GOOGLE_CLIENT_SECRET", () -> "test-only-secret");
        String provider = "spring.security.oauth2.client.provider.google.";
        properties.add(provider + "authorization-uri", () -> base() + "/authorize");
        properties.add(provider + "token-uri", () -> base() + "/token");
        properties.add(provider + "jwk-set-uri", () -> base() + "/jwks");
        properties.add(provider + "user-info-uri", () -> base() + "/userinfo");
    }

    @BeforeEach void resetFixture() { nonce = null; invalidSignature = false; tokenCalls.set(0); }
    @AfterAll static void stopProvider() { PROVIDER.stop(0); }

    @Test void anonymousMeIs401EvenForBrowserHtml() throws Exception {
        mvc.perform(get("/api/me").accept("text/html"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
        verifyNoInteractions(provisioning, currentUsers);
    }

    @Test void authorizationRequestUsesExactCallbackScopesStateAndNonce() throws Exception {
        var start = start();
        assertThat(start.parameters()).containsEntry("redirect_uri", "http://localhost:5173/login/oauth2/code/google")
                .containsEntry("response_type", "code").containsEntry("client_id", CLIENT);
        assertThat(start.parameters().get("scope").split(" ")).containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(start.parameters().get("state")).isNotBlank();
        assertThat(start.parameters().get("nonce")).isNotBlank();
        assertThat(start.parameters()).doesNotContainKeys("access_type", "redirect");
    }

    @Test void completeFrameworkCallbackProvisionsBeforeSessionAndReturnsOnlySafeDto() throws Exception {
        UUID id = UUID.randomUUID();
        var appUser = mock(AppUser.class);
        when(appUser.getId()).thenReturn(id);
        when(provisioning.provision(any())).thenReturn(appUser);
        when(currentUsers.find(id)).thenReturn(Optional.of(new CurrentUser(id,
                "user@example.com", "Example User", "https://example.com/avatar.png")));
        var start = start();
        String oldSessionId = start.session().getId();
        var csrfResponse = mvc.perform(get("/api/csrf").session(start.session())).andReturn();
        String oldCsrf = com.jayway.jsonpath.JsonPath.parse(csrfResponse.getResponse().getContentAsString()).read("$.token");

        mvc.perform(get("/login/oauth2/code/google").session(start.session())
                        .param("code", "test-code").param("state", start.parameters().get("state"))
                        .param("redirect", "https://untrusted.example/"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost:5173/"));
        assertThat(start.session().getId()).isNotEqualTo(oldSessionId);
        assertThat(start.session().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
        assertThat((Object) authorizedClients.loadAuthorizedClient("google", id.toString())).isNull();
        verify(provisioning).provision(new ExternalIdentity(UserProvider.GOOGLE, SUBJECT,
                "user@example.com", "Example User", "https://example.com/avatar.png"));
        var me = mvc.perform(get("/api/me").session(start.session()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.displayName").value("Example User"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.com/avatar.png"))
                .andReturn();
        Map<String, Object> json = com.jayway.jsonpath.JsonPath.parse(me.getResponse().getContentAsString()).read("$");
        assertThat(json.keySet()).containsExactlyInAnyOrder("id", "email", "displayName", "avatarUrl");
        assertThat(me.getResponse().getContentAsString()).doesNotContain(SUBJECT, "test-access-token", "id_token", "JSESSIONID");
        mvc.perform(post("/logout").session(start.session()).header("X-CSRF-TOKEN", oldCsrf))
                .andExpect(status().isForbidden());
        var fresh = mvc.perform(get("/api/csrf").session(start.session())).andReturn();
        String newCsrf = com.jayway.jsonpath.JsonPath.parse(fresh.getResponse().getContentAsString()).read("$.token");
        mvc.perform(post("/logout").session(start.session()).header("X-CSRF-TOKEN", newCsrf))
                .andExpect(status().isNoContent());
        assertThat(start.session().isInvalid()).isTrue();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test void provisioningFailureCannotSaveAuthenticatedSession() throws Exception {
        when(provisioning.provision(any())).thenThrow(new IllegalStateException("private database details"));
        var start = start();
        mvc.perform(get("/login/oauth2/code/google").session(start.session())
                        .param("code", "test-code").param("state", start.parameters().get("state")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/?login=failed"))
                .andExpect(content().string(""));
        assertThat(start.session().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        mvc.perform(get("/api/me").session(start.session())).andExpect(status().isUnauthorized());
    }

    @Test void invalidStateFailsBeforeTokenExchangeAndProvisioning() throws Exception {
        var start = start();
        mvc.perform(get("/login/oauth2/code/google").session(start.session())
                        .param("code", "test-code").param("state", "invalid-state"))
                .andExpect(redirectedUrl("http://localhost:5173/?login=failed"));
        assertThat(tokenCalls.get()).isZero();
        verifyNoInteractions(provisioning);
    }

    @Test void invalidNonceFailsFrameworkValidationBeforeProvisioning() throws Exception {
        var start = start();
        nonce = "wrong-nonce";
        mvc.perform(get("/login/oauth2/code/google").session(start.session())
                        .param("code", "test-code").param("state", start.parameters().get("state")))
                .andExpect(redirectedUrl("http://localhost:5173/?login=failed"));
        assertThat(tokenCalls.get()).isEqualTo(1);
        verifyNoInteractions(provisioning);
    }

    @Test void invalidSignatureFailsFrameworkValidationBeforeProvisioning() throws Exception {
        var start = start();
        invalidSignature = true;
        mvc.perform(get("/login/oauth2/code/google").session(start.session())
                        .param("code", "test-code").param("state", start.parameters().get("state")))
                .andExpect(redirectedUrl("http://localhost:5173/?login=failed"));
        assertThat(tokenCalls.get()).isEqualTo(1);
        verifyNoInteractions(provisioning);
        assertThat(start.session().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    private Start start() throws Exception {
        var result = mvc.perform(get("/oauth2/authorization/google")).andExpect(status().isFound()).andReturn();
        String query = URI.create(result.getResponse().getRedirectedUrl()).getRawQuery();
        var parameters = new java.util.HashMap<String, String>();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            parameters.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        nonce = parameters.get("nonce");
        return new Start((MockHttpSession) result.getRequest().getSession(false), parameters);
    }

    private record Start(MockHttpSession session, Map<String, String> parameters) { }

    private static RSAKey key() {
        try { return new RSAKeyGenerator(2048).keyID("test-key").generate(); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }

    private static String base() { return "http://localhost:" + PROVIDER.getAddress().getPort(); }

    private static HttpServer provider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/jwks", exchange -> respond(exchange, new JWKSet(KEY.toPublicJWK()).toString()));
            server.createContext("/userinfo", exchange -> respond(exchange, """
                    {"sub":"test-google-subject","email":"user@example.com","name":"Example User","picture":"https://example.com/avatar.png"}
                    """));
            server.createContext("/token", exchange -> {
                tokenCalls.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                try {
                    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
                            new JWTClaimsSet.Builder().issuer("https://accounts.google.com").subject(SUBJECT)
                                    .audience(CLIENT).issueTime(Date.from(Instant.now()))
                                    .expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("nonce", nonce).build());
                    jwt.sign(new RSASSASigner(invalidSignature ? key() : KEY));
                    respond(exchange, "{\"access_token\":\"test-access-token\",\"token_type\":\"Bearer\",\"expires_in\":300,"
                            + "\"scope\":\"openid profile email\",\"id_token\":\"" + jwt.serialize() + "\"}");
                } catch (Exception failure) { throw new IOException(failure); }
            });
            server.start();
            return server;
        } catch (IOException failure) { throw new ExceptionInInitializerError(failure); }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
        exchange.close();
    }
}
