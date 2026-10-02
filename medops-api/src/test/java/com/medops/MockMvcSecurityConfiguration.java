package com.medops;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;

/** Supplies Spring Security's MVC argument resolvers and request context to MVC slice tests. */
@TestConfiguration(proxyBeanMethods = false)
@EnableWebSecurity
public class MockMvcSecurityConfiguration {

    @Bean
    MockMvcBuilderCustomizer securityMockMvcBuilderCustomizer() {
        return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
    }

    @Bean
    SecurityFilterChain mockMvcSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/assistant/chat").hasAnyRole("PATIENT", "DOCTOR")
                        .requestMatchers("/api/auth/**", "/api/v1/doctors", "/api/v1/patients").permitAll()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/**", "/api/v1/doctors", "/api/v1/patients"))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .securityContext(Customizer.withDefaults());
        return http.build();
    }
}
