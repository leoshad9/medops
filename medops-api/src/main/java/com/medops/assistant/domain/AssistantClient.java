package com.medops.assistant.domain;

/**
 * Port to the AI assistant backend (medops-ai FastAPI service or its stub).
 * Implementations must not log user messages, prompts, or completions.
 */
public interface AssistantClient {

    /**
     * Sends a user's chat message and returns the assistant's reply.
     *
     * @param userMessage the validated, non-blank user message
     * @return the assistant reply
     */
    AssistantReply chat(String userMessage);
}