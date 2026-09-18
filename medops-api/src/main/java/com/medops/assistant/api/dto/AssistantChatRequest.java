package com.medops.assistant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for the MedOps AI assistant chat.
 *
 * @param message the user's chat message; identity is always derived from the
 *                authenticated principal, never from the request body
 */
public record AssistantChatRequest(
        @NotBlank(message = "Message must not be blank")
        @Size(max = 2000, message = "Message must be 2000 characters or fewer")
        String message) {
}
