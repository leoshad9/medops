package com.medops.assistant.infrastructure;

import java.util.List;

import com.medops.assistant.domain.AssistantAppointment;
import com.medops.assistant.domain.AssistantClient;
import com.medops.assistant.domain.AssistantReply;

/**
 * Deterministic assistant used when the AI service is disabled
 * ({@code MEDOPS_AI_ENABLED=false}). No network calls, no delays.
 */
public class StubAssistantClient implements AssistantClient {

    static final String STUB_REPLY =
            "I'm the MedOps AI Assistant. I can currently help with appointments, reports, "
                    + "prescriptions, billing, and using MedOps.";

    /**
     * Returns the deterministic reply without making a network request.
     *
     * @param userMessage the validated user message
     * @param appointments ignored by the stub
     * @param timeZone ignored by the stub
     * @return the configured stub reply
     */
    @Override
    public AssistantReply chat(
            String userMessage, List<AssistantAppointment> appointments, String timeZone) {
        return new AssistantReply(STUB_REPLY);
    }
}
