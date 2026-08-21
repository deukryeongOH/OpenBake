package com.openbake.ai.infrastructure.config;

import com.openbake.ai.application.EmbeddingProperties;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class OpenAiEmbeddingConfig {

    @Bean
    RestClient openAiRestClient(
            EmbeddingProperties properties,
            @Value("${OPENAI_API_KEY}") String apiKey,
            @Value("${OPENAI_BASE_URL:https://api.openai.com/v1}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .requestFactory(requestFactory)
                .build();
    }
}
