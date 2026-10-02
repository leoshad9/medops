package com.medops.assistant.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medops.MockMvcSecurityConfiguration;
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
@Import(MockMvcSecurityConfiguration.class)
@WebMvcTest(
        controllers = AssistantController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, AuthRateLimitFilter.class}))
@AutoConfigureMockMvc
@SuppressWarnings({"null", "Nullable", "ConstantConditions"})
class AssistantControllerTest {

    private static final String PATIENT_EMAIL = "patient@medops.dev";
    private static final String CHAT_BODY = "{\"message\":\"How do I reschedule an appointment?\"}";
    private static final String CHAT_URI = "/api/v1/assistant/chat";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssistantService assistantService;
    @MockitoBean
    private AppointmentActorResolver actorResolver;

    private final UUID userId = UUID.randomUUID();

    /** Verifies that an authenticated patient receives the assistant reply. */
    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void chatReturnsAssistantReplyForPatient() throws Exception {
        when(actorResolver.requireActiveUser(PATIENT_EMAIL))
                .thenReturn(User.builder().id(userId).email(PATIENT_EMAIL).build());
        when(assistantService.chat(eq(PATIENT_EMAIL), eq("How do I reschedule an appointment?"), isNull()))
                .thenReturn(new AssistantChatResponse("Open the Appointments section to reschedule."));

        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("Open the Appointments section to reschedule."));
    }

    /** Verifies that an authenticated doctor can use the assistant endpoint. */
    @Test
    @WithMockUser(username = "doctor@medops.dev", roles = "DOCTOR")
    void chatReturnsAssistantReplyForDoctor() throws Exception {
        when(assistantService.chat(anyString(), anyString(), any()))
                .thenReturn(new AssistantChatResponse("Reply"));

        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /** Verifies that unauthenticated requests cannot reach the service. */
    @Test
    void chatRejectedForUnauthenticatedUser() throws Exception {
        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isUnauthorized());

        verify(assistantService, never()).chat(anyString(), anyString(), any());
    }

    // NOTE: @PreAuthorize role denial (PATIENT/DOCTOR only) is enforced by method security
    // from the application's SecurityConfig, which is not part of this @WebMvcTest slice -
    // consistent with the other controller tests in this codebase. The unauthenticated
    // case above verifies the endpoint is never public.

    /** Verifies that request validation rejects a whitespace-only message. */
    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void chatRejectedForBlankMessage() throws Exception {
        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verify(assistantService, never()).chat(anyString(), anyString(), any());
    }

    /** Verifies that request validation rejects a message over the size limit. */
    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void chatRejectedForOversizedMessage() throws Exception {
        String oversized = "x".repeat(2001);

        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + oversized + "\"}"))
                .andExpect(status().isBadRequest());

        verify(assistantService, never()).chat(anyString(), anyString(), any());
    }

    /** Verifies that the browser time zone is forwarded for server-side rendering. */
    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void chatForwardsBrowserTimeZoneToService() throws Exception {
        when(assistantService.chat(PATIENT_EMAIL, "How do I reschedule an appointment?", "Asia/Kolkata"))
                .thenReturn(new AssistantChatResponse("Reply"));

        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How do I reschedule an appointment?\","
                                + "\"timeZone\":\"Asia/Kolkata\"}"))
                .andExpect(status().isOk());

        verify(assistantService).chat(PATIENT_EMAIL, "How do I reschedule an appointment?", "Asia/Kolkata");
    }

    /** Verifies that request validation rejects an oversized time zone. */
    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void chatRejectedForOversizedTimeZone() throws Exception {
        String oversized = "x".repeat(65);

        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\",\"timeZone\":\"" + oversized + "\"}"))
                .andExpect(status().isBadRequest());

        verify(assistantService, never()).chat(anyString(), anyString(), any());
    }

    /** Verifies that assistant rate-limit exhaustion is mapped to HTTP 429. */
    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void rateLimitExhaustionMapsTo429() throws Exception {
        when(actorResolver.requireActiveUser(PATIENT_EMAIL))
                .thenReturn(User.builder().id(userId).email(PATIENT_EMAIL).build());
        when(assistantService.chat(anyString(), anyString(), any()))
                .thenThrow(new AssistantRateLimitException());

        mockMvc.perform(post(CHAT_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.status").value("RESOURCE_EXHAUSTED"));
    }
}
