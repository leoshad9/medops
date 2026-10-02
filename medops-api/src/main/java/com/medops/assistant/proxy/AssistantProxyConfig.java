package com.medops.assistant.proxy;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AssistantProxyConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    /**
     * Blocking client for the AI sidecar. HTTP/1.1 is pinned because the sidecar runs
     * uvicorn, which rejects the h2c upgrade the JDK HttpClient attempts by default on
     * plaintext HTTP — same reason as the chat client in
     * {@code AssistantClientConfiguration.HttpAssistantClient}. Both timeouts are
     * explicit so a hung sidecar cannot pin a servlet request thread indefinitely.
     */
    @Bean
    RestClient assistantRestClient(@Value("${assistant.ai.base-url:http://localhost:8000}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
