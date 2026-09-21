package com.medops.assistant.domain;

/**
 * Port to the AI assistant backend (medops-ai FastAPI service or its stub).
 * Implementations must not log user messages, prompts, or completions.
 */
public interface AssistantClient {

    /**
     * Sends a user's chat message, together with a bounded read-only snapshot
     * of that same user's upcoming appointments, lab reports, prescriptions,
     * invoices, and medical records, and returns the reply.
     *
     * @param userMessage the validated, non-blank user message
     * @param context LLM-safe context snapshot of the requesting user, never null
     * @param timeZone IANA zone the snapshot times were rendered in, may be null
     * @return the assistant reply
     */
    AssistantReply chat(String userMessage, AssistantContext context, String timeZone);
}
