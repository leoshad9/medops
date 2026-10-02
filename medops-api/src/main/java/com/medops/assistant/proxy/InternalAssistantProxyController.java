package com.medops.assistant.proxy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InternalAssistantProxyController {

    @Value("${assistant.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @Value("${assistant.internal.token:}")
    private String internalToken;

    private final WebClient webClient;

    // No-arg constructor used by the Spring test context when a WebClient
    // bean is not available. Initialize a simple default WebClient.
    public InternalAssistantProxyController() {
        this.webClient = WebClient.create();
    }

    public void setAiBaseUrl(String aiBaseUrl) {
        this.aiBaseUrl = aiBaseUrl;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    @PostMapping("/api/v1/assistant/actions/invoices/{idx}/pay")
    public ResponseEntity<String> payInvoice(@PathVariable("idx") int idx) {
        String url = aiBaseUrl + "/internal/assistant/actions/invoices/" + idx + "/pay";

        try {
            String responseBody = webClient.post()
                    .uri(url)
                    .header("x-internal-token", internalToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return ResponseEntity.ok(responseBody);
        } catch (WebClientResponseException ex) {
            log.warn("Assistant proxy failed for invoice idx={}: {}", idx, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Assistant proxy failed");
        } catch (RuntimeException ex) {
            log.warn("Assistant proxy failed for invoice idx={}: {}", idx, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Assistant proxy failed");
        }
    }
}
