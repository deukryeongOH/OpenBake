package com.openbake.common.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link RequestIdMdcFilter}를 등록한다.
 *
 * <p>common은 각 서비스의 컴포넌트 스캔 대상이 아니므로(서비스마다 base package가
 * com.openbake.&lt;service&gt;다) 이 설정을 각 서비스에서 명시적으로 import 한다.
 */
@Configuration
public class RequestIdMdcConfig {

    @Bean
    public FilterRegistrationBean<RequestIdMdcFilter> requestIdMdcFilter() {
        FilterRegistrationBean<RequestIdMdcFilter> registration =
                new FilterRegistrationBean<>(new RequestIdMdcFilter());
        registration.setOrder(new RequestIdMdcFilter().getOrder());
        return registration;
    }
}
