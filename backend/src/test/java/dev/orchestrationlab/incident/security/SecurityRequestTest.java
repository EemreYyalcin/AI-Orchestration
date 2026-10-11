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

/**
 * MockMvc ile gerçek Spring Security istek/oturum/CSRF/çıkış kurallarını sınar. İç TestApiController
 * yalnızca test derlemesindedir; production API veya alternatif giriş yolu değildir.
 */
@WebMvcTest(controllers = {HealthController.class, CsrfController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfiguration.class, SecurityRequestTest.TestApiController.class})
class SecurityRequestTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    /**
     * Varsayılan üretilmiş kullanıcı servisinin bulunmadığını doğrular; form/basic giriş mekanizması
     * beklenmez.
     */
    @Test
    void noGeneratedUserDetailsServiceExists() {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
    }

    /**
     * Herkese açık GET sağlık isteğinin oturum veya CSRF olmadan başarılı olduğunu kontrol eder.
     */
    @Test
    void publicHealthNeedsNeitherAuthenticationNorCsrf() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * Kimliksiz korumalı API çağrısının HTML giriş yönlendirmesi olmadan 401 verdiğini doğrular.
     */
    @Test
    void anonymousApiRequestsReturn401WithoutRedirectOrHtml() throws Exception {
        mvc.perform(get("/api/test/protected").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(""));
    }

    /**
     * Oturumlu salt okunur GET’in CSRF istemeden korumalı controller’a ulaştığını doğrular.
     */
    @Test
    void authenticatedGetReachesProtectedControllerWithoutCsrf() throws Exception {
        mvc.perform(get("/api/test/protected").with(user("test-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REACHED"));
    }

    /**
     * Kimliği doğrulanmış ancak erişimi reddedilen kullanıcının 401 yerine 403 aldığını sınar.
     */
    @Test
    void authenticatedAccessDeniedRemains403() throws Exception {
        mvc.perform(get("/api/test/forbidden").with(user("test-user")))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Location"));
    }

    /**
     * Oturum bulunsa da CSRF’siz POST’un 403 verdiğini doğrular.
     */
    @Test
    void unsafeRequestWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/api/test/protected").with(user("test-user")))
                .andExpect(status().isForbidden());
    }

    /**
     * Yanlış CSRF token’ı ile POST’un controller’a ulaşmadan reddedildiğini doğrular.
     */
    @Test
    void unsafeRequestWithInvalidCsrfIsForbidden() throws Exception {
        mvc.perform(post("/api/test/protected").with(user("test-user")).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    /**
     * Oturum ve geçerli CSRF birlikte verildiğinde POST’un controller’a ulaştığını doğrular.
     */
    @Test
    void unsafeRequestWithValidCsrfReachesProtectedController() throws Exception {
        mvc.perform(post("/api/test/protected").with(user("test-user")).with(csrf()))
                .andExpect(status().isOk());
    }

    /**
     * Geçerli CSRF’nin kullanıcı oturumunun yerine geçmediğini; kimliksiz API çağrısının 401 aldığını
     * sınar.
     */
    @Test
    void csrfDoesNotAuthenticateAnAnonymousRequest() throws Exception {
        mvc.perform(post("/api/test/protected").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Kimliksiz POST’ta bile CSRF filtresinin yetkilendirmeden önce 403 üretebildiğini doğrular.
     */
    @Test
    void anonymousUnsafeRequestStillRequiresCsrfBeforeAuthorization() throws Exception {
        mvc.perform(post("/api/test/protected"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Location"));
    }

    /**
     * Gerçek /api/csrf yanıtındaki token/başlıkla aynı oturumda POST yapıldığını doğrular.
     */
    @Test
    void csrfEndpointExposesSessionTokenThatCanBeSubmittedInHeader() throws Exception {
        MockHttpSession session = authenticatedSession();
        var result = mvc.perform(get("/api/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        // Gerçek endpoint’in maskelenmiş oturum token’ını kullan; test üretimi CSRF’ye dayanma.
        var json = com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString());
        String token = json.read("$.token");
        String headerName = json.read("$.headerName");
        mvc.perform(post("/api/test/protected").session(session).header(headerName, token))
                .andExpect(status().isOk());
    }

    /**
     * Giriş öncesi tarayıcının CSRF endpoint’inden token ve oturum başlatabildiğini sınar; bu kimlik
     * doğrulama değildir.
     */
    @Test
    void anonymousBrowserCanBootstrapCsrfSession() throws Exception {
        var result = mvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    /**
     * CSRF’siz çıkışın 403 ile reddedilip mevcut oturumun doğrulanmış kalmasını kontrol eder.
     */
    @Test
    void logoutWithoutCsrfIsRejectedAndKeepsSessionAuthentication() throws Exception {
        MockHttpSession session = authenticatedSession();
        mvc.perform(post("/logout").session(session))
                .andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
        mvc.perform(get("/api/test/protected").session(session)).andExpect(status().isOk());
    }

    /**
     * Gerçek CSRF token’ıyla çıkışın 204 verdiğini, oturumu kapattığını ve JSESSIONID çerezini
     * sildiğini doğrular. Eski token yeni oturumda kullanılamamalıdır.
     */
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

    /**
     * GET /logout’un çıkış veya onay sayfası açmadığını; oturumun korunduğunu doğrular.
     */
    @Test
    void getLogoutCannotLogOutOrDisplayConfirmationPage() throws Exception {
        MockHttpSession session = authenticatedSession();
        mvc.perform(get("/logout").session(session))
                .andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
    }

    /**
     * Yalnızca test için doğrulanmış SecurityContext taşıyan MockHttpSession oluşturur.
     */
    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        var principal = User.withUsername("test-user").password("unused").authorities("TEST_ONLY").build();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    // Bu fixture yalnızca testte derlenir; production API veya alternatif giriş ucu değildir.
    /**
     * Yalnızca testte derlenen uçlarla Security zincirinin GET/POST/erişim reddi davranışını görünür
     * kılar; production API’ye eklenmez.
     */
    @RestController
    static class TestApiController {
        /**
         * GET isteğinin güvenlik zincirini geçtiğini REACHED yanıtıyla gösterir.
         */
        @GetMapping("/api/test/protected")
        Map<String, String> read() { return Map.of("status", "REACHED"); }

        /**
         * POST isteğinin oturum ve CSRF denetimini geçtiğini REACHED yanıtıyla gösterir.
         */
        @PostMapping("/api/test/protected")
        Map<String, String> write() { return Map.of("status", "REACHED"); }

        /**
         * Doğrulanmış istekte erişim reddi üretir; test bunun 403 olarak kaldığını doğrular.
         */
        @GetMapping("/api/test/forbidden")
        void forbidden() { throw new AccessDeniedException("Test-only refusal"); }
    }
}
