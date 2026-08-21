package com.openbake.product.infrastructure.ai;

import com.openbake.product.application.SearchProperties;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SearchProperties.class)
public class AiServiceSearchClientConfig {

    @Bean
    RestClient aiServiceSearchRestClient(
            SearchProperties properties,
            @Value("${AI_SERVICE_URL}") String aiServiceUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.semantic().timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.semantic().timeout());
        return RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
