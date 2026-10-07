package io.github.harshmittal.urlshortener.app;

import io.github.harshmittal.urlshortener.shared.audit.domain.AuditTrail;
import io.github.harshmittal.urlshortener.shared.identity.adapter.in.web.ApiKeyAuthenticationFilter;
import io.github.harshmittal.urlshortener.shared.identity.adapter.in.web.ProblemAccessDeniedHandler;
import io.github.harshmittal.urlshortener.shared.identity.adapter.in.web.ProblemAuthenticationEntryPoint;
import io.github.harshmittal.urlshortener.shared.identity.domain.Authenticator;
import io.github.harshmittal.urlshortener.shared.web.RequestAuditContexts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless API-key security (R3, R4). {@code POST /api/keys} is admin-only, link endpoints are
 * owner-only, every other {@code /api/**} path is denied (default-deny), and everything else
 * (redirects) is public.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Authenticator authenticator,
            AuditTrail audit,
            RequestAuditContexts auditContexts,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
            throws Exception {
        // Created here rather than as a bean, so Spring Boot does not also register it as a
        // servlet filter for every path.
        var apiKeyFilter = new ApiKeyAuthenticationFilter(authenticator);
        return http.csrf(AbstractHttpConfigurer::disable) // no cookies or sessions to forge
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.requestMatchers(HttpMethod.POST, "/api/keys")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/links", "/api/links/**")
                        .hasRole("OWNER")
                        // Default-deny: an /api route with no rule above is refused, even with a valid key.
                        .requestMatchers("/api", "/api/**")
                        .denyAll()
                        .anyRequest()
                        .permitAll())
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                new ProblemAuthenticationEntryPoint(audit, auditContexts, exceptionResolver))
                        .accessDeniedHandler(new ProblemAccessDeniedHandler(audit, auditContexts, exceptionResolver)))
                .build();
    }
}
