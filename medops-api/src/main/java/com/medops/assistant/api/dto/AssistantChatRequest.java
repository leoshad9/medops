package com.medops.assistant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for the MedOps AI assistant chat.
 *
 * @param message the user's chat message; identity is always derived from the
 *                authenticated principal, never from the request body
 * @param timeZone optional IANA time zone from the browser, used only to render
 *                 the user's own appointment times; never trusted for identity
 */
public record AssistantChatRequest(
        @NotBlank(message = "Message must not be blank")
        @Size(max = 2000, message = "Message must be 2000 characters or fewer")
        String message,

        @Size(max = 64, message = "Time zone must be 64 characters or fewer")
        String timeZone) {
}
