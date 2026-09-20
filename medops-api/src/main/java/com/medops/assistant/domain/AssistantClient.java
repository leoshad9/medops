package com.medops.assistant.domain;

import java.util.List;

/**
 * Port to the AI assistant backend (medops-ai FastAPI service or its stub).
 * Implementations must not log user messages, prompts, or completions.
 */
public interface AssistantClient {

    /**
     * Sends a user's chat message, together with a bounded read-only snapshot
     * of that same user's upcoming appointments, and returns the reply.
     *
     * @param userMessage the validated, non-blank user message
     * @param appointments LLM-safe appointments of the requesting user, never null
     * @param timeZone IANA zone the snapshot times were rendered in, may be null
     * @return the assistant reply
     */
    AssistantReply chat(String userMessage, List<AssistantAppointment> appointments, String timeZone);
}
