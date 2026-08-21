package com.openbake.ai.infrastructure.config;

import com.openbake.ai.application.RecommendationProperties;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CoreRecommendationClientConfig {

    @Bean
    RestClient coreRecommendationRestClient(
            RecommendationProperties properties,
            @Value("${CORE_SERVICE_URL}") String coreServiceUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.coreTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.coreTimeout());
        return RestClient.builder()
                .baseUrl(coreServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
