package dev.orchestrationlab.incident.security;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.orchestrationlab.incident.authentication.controller.CsrfController;
import dev.orchestrationlab.incident.configuration.SecurityConfiguration;
import dev.orchestrationlab.incident.controller.HealthController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {HealthController.class, CsrfController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfiguration.class, SecurityRequestTest.TestApiController.class})
class SecurityRequestTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void noGeneratedUserDetailsServiceExists() {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
    }

    @Test
    void publicHealthNeedsNeitherAuthenticationNorCsrf() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void anonymousApiRequestsReturn401WithoutRedirectOrHtml() throws Exception {
        mvc.perform(get("/api/test/protected").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(""));
    }

    @Test
    void authenticatedGetReachesProtectedControllerWithoutCsrf() throws Exception {
        mvc.perform(get("/api/test/protected").with(user("test-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REACHED"));
    }

    @Test
    void authenticatedAccessDeniedRemains403() throws Exception {
        mvc.perform(get("/api/test/forbidden").with(user("test-user")))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void unsafeRequestWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/api/test/protected").with(user("test-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsafeRequestWithInvalidCsrfIsForbidden() throws Exception {
        mvc.perform(post("/api/test/protected").with(user("test-user")).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsafeRequestWithValidCsrfReachesProtectedController() throws Exception {
        mvc.perform(post("/api/test/protected").with(user("test-user")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void csrfDoesNotAuthenticateAnAnonymousRequest() throws Exception {
        mvc.perform(post("/api/test/protected").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousUnsafeRequestStillRequiresCsrfBeforeAuthorization() throws Exception {
        mvc.perform(post("/api/test/protected"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void csrfEndpointExposesSessionTokenThatCanBeSubmittedInHeader() throws Exception {
        MockHttpSession session = authenticatedSession();
        var result = mvc.perform(get("/api/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        // Use the real endpoint's masked token, not a test-generated CSRF token.
        var json = com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString());
        String token = json.read("$.token");
        String headerName = json.read("$.headerName");
        mvc.perform(post("/api/test/protected").session(session).header(headerName, token))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousBrowserCanBootstrapCsrfSession() throws Exception {
        var result = mvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void logoutWithoutCsrfIsRejectedAndKeepsSessionAuthentication() throws Exception {
        MockHttpSession session = authenticatedSession();
        mvc.perform(post("/logout").session(session))
                .andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
        mvc.perform(get("/api/test/protected").session(session)).andExpect(status().isOk());
    }

    @Test
    void logoutWithRealSessionTokenInvalidatesSessionAndClearsCookie() throws Exception {
        MockHttpSession session = authenticatedSession();
        var result = mvc.perform(get("/api/csrf").session(session)).andReturn();
        var json = com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString());
        String token = json.read("$.token");
        mvc.perform(post("/logout").session(session).header("X-CSRF-TOKEN", token))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(cookie().path("JSESSIONID", "/"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/test/protected")).andExpect(status().isUnauthorized());

        MockHttpSession freshSession = authenticatedSession();
        mvc.perform(post("/api/test/protected").session(freshSession).header("X-CSRF-TOKEN", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/csrf").session(freshSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void getLogoutCannotLogOutOrDisplayConfirmationPage() throws Exception {
        MockHttpSession session = authenticatedSession();
        mvc.perform(get("/logout").session(session))
                .andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        var principal = User.withUsername("test-user").password("unused").authorities("TEST_ONLY").build();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    // Fixtures compiled only into test classes; no production API or login endpoint.
    @RestController
    static class TestApiController {
        @GetMapping("/api/test/protected")
        Map<String, String> read() { return Map.of("status", "REACHED"); }

        @PostMapping("/api/test/protected")
        Map<String, String> write() { return Map.of("status", "REACHED"); }

        @GetMapping("/api/test/forbidden")
        void forbidden() { throw new AccessDeniedException("Test-only refusal"); }
    }
}
