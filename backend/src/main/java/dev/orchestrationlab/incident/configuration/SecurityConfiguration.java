package dev.orchestrationlab.incident.configuration;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import dev.orchestrationlab.incident.authentication.infrastructure.oidc.DiscardingAuthorizedClientRepository;
import dev.orchestrationlab.incident.authentication.infrastructure.oidc.GoogleOidcUserService;
import dev.orchestrationlab.incident.user.application.UserProvisioningService;

/**
 * Servlet HTTP güvenlik zincirini tanımlar: Google OIDC girişi, sunucu oturumu, CSRF ve izinli yollar.
 * API kimliksiz çağrılarda 401 alır; form/basic giriş kapalıdır ve kalan yollar reddedilir.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    /**
     * İstemci kaydı varsa OIDC girişini ve kullanıcı kayıt adaptörünü bağlar. Sağlık/CSRF ve Google
     * protokol yollarını açar, diğer API yollarını oturumla korur. Oturum kimliğini girişte yeniler;
     * POST /logout CSRF denetiminden sonra oturumu ve çerezi temizleyip 204 döndürür. Google kaydı
     * yokken yerel altyapı yine başlatılabilir.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> registrations,
            ObjectProvider<UserProvisioningService> provisioning) throws Exception {
        // Yerel altyapı dış kimlik bilgisi olmadan açılır; giriş için google profili gerekir.
        if (registrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .authorizedClientRepository(new DiscardingAuthorizedClientRepository())
                    .loginPage("http://localhost:5173/")
                    .loginProcessingUrl("/login/oauth2/code/google")
                    .defaultSuccessUrl("http://localhost:5173/", true)
                    .failureUrl("http://localhost:5173/?login=failed")
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(
                            new GoogleOidcUserService(new OidcUserService(), provisioning.getObject()))));
        }
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/csrf",
                                "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/oauth2/authorization/google", "/login/oauth2/code/google")
                        .permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .sessionManagement(sessions -> sessions
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, "/logout"))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .build();
    }
}
