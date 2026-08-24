package com.openbake.order.infrastructure.client;

import static com.openbake.common.security.service.ServiceAuthenticationHeaders.CORE_SERVICE;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_NAME;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_TOKEN;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/** Order가 내부 서비스에 요청할 때 사용하는 core-service 인증 설정. */
public class CoreServiceFeignConfiguration {

    @Bean
    RequestInterceptor coreServiceAuthenticationInterceptor(
            @Value("${CORE_SERVICE_TOKEN}") String coreServiceToken) {
        if (coreServiceToken == null || coreServiceToken.isBlank()) {
            throw new IllegalArgumentException("Core service token must be configured");
        }

        return template -> {
            template.header(SERVICE_NAME, CORE_SERVICE);
            template.header(SERVICE_TOKEN, coreServiceToken);
        };
    }
}
