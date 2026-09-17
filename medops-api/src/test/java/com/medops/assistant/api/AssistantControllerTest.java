package com.medops.assistant.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.assistant.api.dto.AssistantChatResponse;
import com.medops.assistant.application.AssistantService;
import com.medops.assistant.domain.AssistantRateLimitException;
import com.medops.auth.domain.User;
import com.medops.auth.security.filters.AuthRateLimitFilter;
import com.medops.auth.security.jwt.JwtAuthenticationFilter;

/**
 * HTTP-level tests for {@link AssistantController}: request validation, the
 * {@code ApiResponse} envelope, role authorization, and error mapping. Security
 * filters (JWT / auth rate limit) are excluded as in {@code NotificationControllerTest};
 * the filter chain's own behaviour is out of scope for this slice.
 */
@WebMvcTest(
        controllers = AssistantController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, AuthRateLimitFilter.class}))
@AutoConfigureMockMvc
@SuppressWarnings({"null", "Nullable", "ConstantConditions"})
class AssistantControllerTest {

    private static final @NonNull String PATIENT_EMAIL = "patient@medops.dev";
    private static final @NonNull String CHAT_BODY = "{\"message\":\"How do I reschedule an appointment?\"}";
    private static final String CHAT_URI = "/api/v1/assistant/chat";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssistantService assistantService;
    @MockitoBean
    private AppointmentActorResolver actorResolver;

    private final UUID userId = UUID.randomUUID();

    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void chatReturnsAssistantReplyForPatient() throws Exception {
        when(actorResolver.requireActiveUser(PATIENT_EMAIL))
                .thenReturn(User.builder().id(userId).email(PATIENT_EMAIL).build());
        when(assistantService.chat(PATIENT_EMAIL, "How do I reschedule an appointment?"))
                .thenReturn(new AssistantChatResponse("Open the Appointments section to reschedule."));

        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("Open the Appointments section to reschedule."));
    }

    @Test
    @WithMockUser(username = "doctor@medops.dev", roles = "DOCTOR")
    void chatReturnsAssistantReplyForDoctor() throws Exception {
        when(assistantService.chat(anyString(), anyString()))
                .thenReturn(new AssistantChatResponse("Reply"));

        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void chatRejectedForUnauthenticatedUser() throws Exception {
        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isUnauthorized());

        verify(assistantService, never()).chat(anyString(), anyString());
    }

    // NOTE: @PreAuthorize role denial (PATIENT/DOCTOR only) is enforced by method security
    // from the application's SecurityConfig, which is not part of this @WebMvcTest slice -
    // consistent with the other controller tests in this codebase. The unauthenticated
    // case above verifies the endpoint is never public.

    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void chatRejectedForBlankMessage() throws Exception {
        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verify(assistantService, never()).chat(anyString(), anyString());
    }

    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void chatRejectedForOversizedMessage() throws Exception {
        String oversized = "x".repeat(2001);

        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + oversized + "\"}"))
                .andExpect(status().isBadRequest());

        verify(assistantService, never()).chat(anyString(), anyString());
    }

    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void rateLimitExhaustionMapsTo429() throws Exception {
        when(actorResolver.requireActiveUser(PATIENT_EMAIL))
                .thenReturn(User.builder().id(userId).email(PATIENT_EMAIL).build());
        when(assistantService.chat(anyString(), anyString()))
                .thenThrow(new AssistantRateLimitException());

        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.status").value("RESOURCE_EXHAUSTED"));
    }
}