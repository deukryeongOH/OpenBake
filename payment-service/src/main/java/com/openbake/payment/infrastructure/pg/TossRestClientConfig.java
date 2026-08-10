package com.openbake.payment.infrastructure.pg;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 토스페이먼츠 전용 RestClient 빈 설정.
 * 타임아웃, 인증 헤더, baseUrl을 여기서 구성한다.
 * 테스트에서는 이 빈을 @MockitoBean 또는 @TestConfiguration으로 교체한다.
 */
@Configuration
class TossRestClientConfig {

    @Bean
    RestClient tossRestClient(
            @Value("${payment.toss.secret-key}") String secretKey,
            @Value("${payment.toss.connect-timeout}") int connectTimeout,
            @Value("${payment.toss.read-timeout}") int readTimeout) {

        String encoded = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl("https://api.tosspayments.com/v1/payments")
                .defaultHeader("Authorization", "Basic " + encoded)
                .requestFactory(factory)
                .build();
    }
}
