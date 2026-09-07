package com.medops.auth.dto;

import java.util.UUID;

/**
 * Public user identity returned in auth responses. The frontend stores this in
 * React context (not in localStorage) — the actual access/refresh tokens live
 * in HttpOnly cookies set by the controller, so they are never readable by JavaScript.
 *
 * @param id    user identifier
 * @param email user email (also the JWT subject)
 * @param role  primary role name (DOCTOR or PATIENT)
 */
public record UserInfo(UUID id, String email, String role) {
}
