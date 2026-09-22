package com.medops.me.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medops.auth.security.principal.MedOpsUser;
import com.medops.me.api.dto.MeResponse;
import com.medops.me.application.MeBootstrapService;
import com.medops.shared.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * Session bootstrap for the SPA: the identity returned by {@code GET /api/auth/me}
 * plus the role-specific profile, in a single request. Replaces the serial
 * {@code /auth/me -> /v1/{patients|doctors}/me} pair that used to gate the first render.
 * Unlike {@code /api/auth/me} this endpoint is not public, so an unauthenticated call
 * is rejected by the filter chain before reaching the controller.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final MeBootstrapService meBootstrapService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<MeResponse>> me(@AuthenticationPrincipal MedOpsUser principal) {
        if (principal == null) {
            // Only reachable if the request was authenticated with a principal that is
            // not the application's MedOpsUser.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(ApiResponse.success(meBootstrapService.load(principal), "Session active"));
    }
}
