package com.medops.billing.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.medops.MockMvcSecurityConfiguration;
import com.medops.auth.security.filters.AuthRateLimitFilter;
import com.medops.auth.security.jwt.JwtAuthenticationFilter;
import com.medops.billing.application.StripePaymentService;

/**
 * HTTP-level tests for the Stripe webhook endpoint: the endpoint is public
 * (Stripe has no user JWT), so signature verification is its only auth.
 * A missing signature or a failed verification must be rejected, a verified
 * event acked with 200. Business rejections inside the handler are also
 * acked 200 so Stripe stops retrying non-retryable states.
 */
@Import(MockMvcSecurityConfiguration.class)
@WebMvcTest(
        controllers = StripeWebhookController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, AuthRateLimitFilter.class}))
@AutoConfigureMockMvc
class StripeWebhookControllerTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @MockitoBean
    private StripePaymentService stripePaymentService;

    @Test
    void webhook_returns400_whenSignatureHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"checkout.session.completed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Missing Stripe-Signature header"));
    }

    @Test
    void webhook_returns400_whenSignatureInvalid() throws Exception {
        org.mockito.Mockito.doThrow(
                        new com.medops.shared.exception.InvalidRequestException("Invalid webhook signature"))
                .when(stripePaymentService).handleWebhook("payload", "t=badsig");

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .with(csrf())
                        .header("Stripe-Signature", "t=badsig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("payload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Invalid webhook signature"));
    }

    @Test
    void webhook_returns200_forVerifiedEvent() throws Exception {
        org.mockito.Mockito.doNothing().when(stripePaymentService).handleWebhook("payload", "t=validsig");

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .with(csrf())
                        .header("Stripe-Signature", "t=validsig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("payload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

