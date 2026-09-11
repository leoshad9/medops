package com.medops.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1tZWRvcHMtdGVzdGluZy1vbmx5LTMyYnl0ZQ==";

    @Test
    void generatedTokenContainsIssuerAndAudience() {
        JwtService jwtService = new JwtService(new JwtProperties(
                SECRET, 900000, 28800000, "medops-api", "medops-web"));
        User userDetails = new User(
                "patient@medops.dev", "password",
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.extractUsername(token)).isEqualTo("patient@medops.dev");
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void rejectsTokenWithWrongIssuer() {
        JwtService jwtService = new JwtService(new JwtProperties(
                SECRET, 900000, 28800000, "medops-api", "medops-web"));
        User userDetails = new User(
                "patient@medops.dev", "password",
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
        String token = new JwtService(new JwtProperties(
                SECRET, 900000, 28800000, "other-api", "medops-web")).generateAccessToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
    }

    @Test
    void rejectsTokenWithWrongAudience() {
        JwtService jwtService = new JwtService(new JwtProperties(
                SECRET, 900000, 28800000, "medops-api", "medops-web"));
        User userDetails = new User(
                "patient@medops.dev", "password",
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
        String token = new JwtService(new JwtProperties(
                SECRET, 900000, 28800000, "medops-api", "other-web")).generateAccessToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
    }
}
