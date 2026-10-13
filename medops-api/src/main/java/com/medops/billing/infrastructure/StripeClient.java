package com.medops.billing.infrastructure;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.billing.api.dto.StripeCheckoutSessionRequest;
import com.medops.billing.api.dto.StripeCheckoutSessionResult;
import com.medops.billing.api.dto.StripeWebhookEvent;
import com.medops.shared.exception.InvalidRequestException;
import com.medops.shared.exception.ServiceUnavailableException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StripeClient {

    private final StripeProperties properties;
    private final ObjectMapper objectMapper;

    public StripeCheckoutSessionResult createCheckoutSession(StripeCheckoutSessionRequest request) {
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setUiMode(SessionCreateParams.UiMode.ELEMENTS)
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(request.currency())
                                    .setUnitAmount(request.amount())
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData
                                            .builder()
                                            .setName(request.productName())
                                            .build())
                                    .build())
                            .build())
                    .putAllMetadata(request.metadata())
                    .setReturnUrl(properties.returnUrl())
                    .build();

            Session session = Session.create(params, requestIdempotencyKey(request.idempotencyKey()));
            return new StripeCheckoutSessionResult(
                    session.getId(),
                    session.getClientSecret(),
                    session.getAmountTotal(),
                    session.getCurrency(),
                    mapMetadata(session.getMetadata()));
        } catch (StripeException ex) {
            log.error("Stripe API error: {}", ex.getMessage(), ex);
            throw new ServiceUnavailableException("Payment gateway error. Please try again.");
        }
    }

    public StripeWebhookEvent constructEvent(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, properties.webhookSecret());
        } catch (SignatureVerificationException ex) {
            log.warn("Stripe webhook signature verification failed: {}", ex.getMessage());
            throw new InvalidRequestException("Invalid webhook signature");
        }

        return parseStripeEvent(event);
    }

    private StripeWebhookEvent parseStripeEvent(Event event) {
        String type = event.getType();
        String referenceId = null;
        Long amount = null;
        String currency = null;
        Map<String, String> metadata = new HashMap<>();

        if (event.getDataObjectDeserializer().getObject().isPresent()) {
            com.stripe.model.StripeObject obj = event.getDataObjectDeserializer().getObject().get();
            if (obj instanceof Session session) {
                referenceId = session.getId();
                amount = session.getAmountTotal();
                currency = session.getCurrency();
                if (session.getMetadata() != null) {
                    session.getMetadata().forEach(metadata::put);
                }
            } else if (obj instanceof PaymentIntent pi) {
                referenceId = pi.getId();
                amount = pi.getAmount();
                currency = pi.getCurrency();
                if (pi.getMetadata() != null) {
                    pi.getMetadata().forEach(metadata::put);
                }
            } else {
                JsonNode dataObject = objectMapper.valueToTree(obj);
                referenceId = dataObject.path("id").asText(null);
                JsonNode totalNode = dataObject.path("amount_total");
                amount = totalNode.isMissingNode()
                        ? dataObject.path("amount").asLong(0)
                        : totalNode.asLong(0);
                currency = dataObject.path("currency").asText(null);
                JsonNode metaNode = dataObject.path("metadata");
                if (metaNode.isObject()) {
                    metaNode.properties().forEach(e -> metadata.put(e.getKey(), e.getValue().asText()));
                }
            }
        } else {
            log.warn("Stripe event payload could not be deserialized: type={}", type);
        }

        return new StripeWebhookEvent(type, referenceId, amount, currency, metadata);
    }

    private StripeCheckoutSessionResult.Metadata mapMetadata(Map<String, String> metadata) {
        return new StripeCheckoutSessionResult.Metadata(
                metadata.get("invoice_id"),
                metadata.get("patient_profile_id"),
                metadata.get("actor_user_id"),
                metadata.get("actor_email"));
    }

    private com.stripe.net.RequestOptions requestIdempotencyKey(String key) {
        return com.stripe.net.RequestOptions.builder().setIdempotencyKey(key).build();
    }
}
