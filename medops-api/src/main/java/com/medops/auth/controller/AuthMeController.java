package com.medops.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medops.auth.dto.UserInfo;
import com.medops.auth.security.MedOpsUser;
import com.medops.shared.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * Returns the identity of the currently-authenticated user. The frontend calls this
 * on mount to restore the session from the HttpOnly access-token cookie — since the
 * cookie is not readable by JavaScript, the client has no other way to know who is
 * signed in after a page reload.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthMeController {

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfo>> me(@AuthenticationPrincipal MedOpsUser principal) {
        if (principal == null) {
            return ResponseEntity.noContent().build();
        }
        UserInfo info = new UserInfo(principal.getId(), principal.getUsername(), principal.getRoleName());
        return ResponseEntity.ok(ApiResponse.success(info, "Session active"));
    }
}
