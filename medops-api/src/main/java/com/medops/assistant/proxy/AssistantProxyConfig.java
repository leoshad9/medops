package com.medops.assistant.proxy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AssistantProxyConfig {

    @Bean
    WebClient assistantWebClient(@Value("${assistant.ai.base-url:http://localhost:8000}") String baseUrl) {
        WebClient.Builder builder = WebClient.builder();
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
