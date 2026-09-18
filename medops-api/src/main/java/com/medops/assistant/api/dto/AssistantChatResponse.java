package com.medops.assistant.api.dto;

/**
 * Response payload containing the AI assistant's reply.
 *
 * @param message the assistant's reply text
 */
public record AssistantChatResponse(String message) {
}
