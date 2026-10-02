package com.medops.assistant.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

public class InternalAssistantProxyControllerTest {

    @Test
    public void payInvoice_forwardsRequest_andReturnsBody() {
        ExchangeFunction exchange = request -> {
            assertThat(request.url().toString()).isEqualTo("http://localhost:8000/internal/assistant/actions/invoices/0/pay");
            assertThat(request.headers().getFirst("x-internal-token")).isEqualTo("secret");
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"status\":\"ok\"}")
                    .build());
        };

        WebClient webClient = WebClient.builder().exchangeFunction(exchange).build();
        InternalAssistantProxyController ctrl = new InternalAssistantProxyController(webClient);
        ctrl.setAiBaseUrl("http://localhost:8000");
        ctrl.setInternalToken("secret");

        var resp = ctrl.payInvoice(0);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo("{\"status\":\"ok\"}");
    }
}
