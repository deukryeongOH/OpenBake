package com.openbake.order.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 정산 내부 API 호출용 RestClient.
 * 모듈러 모놀리식이라 base-url 은 자기 자신(localhost)이지만, MSA 전환 시 이 값만 바꾸면 된다.
 */
@Configuration
public class SettlementRestClientConfig {

    @Bean
    public RestClient settlementRestClient(
            @Value("${openbake.settlement.base-url:http://localhost:8080}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
