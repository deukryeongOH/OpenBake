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

    /**
     * 요청 하나당 로그 한 줄. {@link RequestIdMdcFilter} 바로 뒤에 온다.
     *
     * <p>이것이 없으면 traceId를 로그에 넣어도 <b>흘려보낼 로그 자체가 없어</b>
     * 추적에서 로그로 넘어갈 수 없다.
     */
    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setOrder(new RequestLoggingFilter().getOrder());
        return registration;
    }
}
