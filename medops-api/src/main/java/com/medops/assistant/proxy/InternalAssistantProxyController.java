package com.medops.assistant.proxy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * Proxies the invoice-payment action to the MedOps FastAPI AI sidecar.
 *
 * <p>Uses the blocking {@link RestClient} rather than a reactive client: the endpoint
 * is served by the servlet stack, so a reactive client would only add the Netty and
 * Reactor dependency surface to gain a {@code Mono} that has to be blocked on again.
 */
@Slf4j
@RestController
public class InternalAssistantProxyController {

    private static final String INTERNAL_TOKEN_HEADER = "x-internal-token";
    private static final String PAY_INVOICE_URI = "/internal/assistant/actions/invoices/{idx}/pay";
    private static final String PROXY_FAILED_BODY = "Assistant proxy failed";

    private final RestClient assistantRestClient;
    private final String internalToken;

    InternalAssistantProxyController(
            @Qualifier("assistantRestClient") RestClient assistantRestClient,
            @Value("${assistant.internal.token:}") String internalToken) {
        this.assistantRestClient = assistantRestClient;
        this.internalToken = internalToken;
    }

    @PostMapping("/api/v1/assistant/actions/invoices/{idx}/pay")
    public ResponseEntity<String> payInvoice(@PathVariable("idx") int idx) {
        var request = assistantRestClient.post()
                .uri(PAY_INVOICE_URI, idx)
                .header("Accept", "application/json");
        if (StringUtils.hasText(internalToken)) {
            request.header(INTERNAL_TOKEN_HEADER, internalToken);
        }

        try {
            String responseBody = request.retrieve().body(String.class);
            return ResponseEntity.ok(responseBody);
        } catch (RestClientException ex) {
            // Covers upstream error statuses (RestClientResponseException) and transport
            // failures (ResourceAccessException) alike: the caller learns only that the
            // sidecar could not be reached, never the sidecar's own error detail.
            log.warn("Assistant proxy failed for invoice idx={}: {}", idx, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(PROXY_FAILED_BODY);
        }
    }
}
