package com.medops.assistant.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

public class InternalAssistantProxyControllerTest {

    private static final String BASE_URL = "http://localhost:8000";
    private static final String PAY_URI = BASE_URL + "/internal/assistant/actions/invoices/{idx}/pay";

    @Test
    public void payInvoice_forwardsRequest_andReturnsBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(PAY_URI.replace("{idx}", "0")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-internal-token", "secret"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        InternalAssistantProxyController ctrl = new InternalAssistantProxyController(builder.build(), "secret");

        var resp = ctrl.payInvoice(0);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo("{\"status\":\"ok\"}");
        server.verify();
    }

    @Test
    public void payInvoice_returnsBadGateway_whenSidecarFails() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(PAY_URI.replace("{idx}", "7")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        InternalAssistantProxyController ctrl = new InternalAssistantProxyController(builder.build(), "secret");

        var resp = ctrl.payInvoice(7);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(resp.getBody()).isEqualTo("Assistant proxy failed");
        server.verify();
    }
}
