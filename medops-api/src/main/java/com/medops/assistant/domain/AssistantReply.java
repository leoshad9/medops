package com.medops.assistant.domain;

/**
 * Plain-language reply produced by the AI assistant. Implementations must not
 * log the reply text.
 *
 * @param text the assistant reply text
 */
public record AssistantReply(String text) {
}
