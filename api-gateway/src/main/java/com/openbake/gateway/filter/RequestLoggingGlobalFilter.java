package com.openbake.gateway.filter;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 요청 하나당 로그 한 줄을 남긴다. 서블릿 서비스의 {@code RequestLoggingFilter}에
 * 대응하는 WebFlux 판이다.
 *
 * <p><b>왜 게이트웨이에 따로 두나.</b> 게이트웨이는 WebFlux라 서블릿 필터
 * ({@code OncePerRequestFilter})를 쓸 수 없다. 그리고 {@code common} 모듈을 의존할
 * 수도 없다 — 그쪽이 {@code spring-boot-starter-webmvc}를 노출해서, 넣으면 Netty
 * 대신 Tomcat이 뜨며 게이트웨이가 통째로 깨진다.
 *
 * <p><b>게이트웨이 로그가 특히 중요한 이유.</b> 모든 외부 요청이 여기를 지난다.
 * 뒤쪽 서비스가 조용해도 게이트웨이 로그만 있으면 "언제 어떤 요청이 들어와 몇 ms에
 * 어떤 상태로 끝났는가"를 알 수 있다.
 *
 * <p>또 하나. 게이트웨이 로그의 {@code requestId} 자리는 원리적으로 비어 있다
 * (MDC가 ThreadLocal이라 리액터 스레드 전환을 못 넘는다). 대신 <b>traceId는 채워진다</b>
 * — Micrometer Tracing이 Reactor Context 전파를 지원하기 때문이다. 그래서 이 로그가
 * 추적과 이어지는 연결 고리가 된다.
 *
 * <p>본문은 남기지 않는다. 개인정보·결제정보가 그대로 들어가고 양도 감당할 수 없다.
 * 쿼리 문자열도 뺀다 — 검색어나 토큰이 들어올 수 있다.
 */
@Component
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingGlobalFilter.class);

    /**
     * 로그를 남기지 않을 경로.
     *
     * <p>헬스체크는 쿠버네티스가 15초마다 찌른다. 이것까지 남기면 하루 수만 줄이
     * 쌓이고 그 안에서 진짜 요청을 찾기가 오히려 어려워진다.
     */
    private static final Set<String> SKIP_PREFIXES = Set.of("/actuator", "/favicon.ico");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return chain.filter(exchange);
            }
        }

        String method = exchange.getRequest().getMethod().name();
        long startNanos = System.nanoTime();

        // doFinally에 두는 이유: 정상 종료·오류·취소 어느 경로로 끝나도 기록을
        // 남긴다. 특히 오류로 끝난 요청이 조사에 가장 필요하다.
        return chain.filter(exchange).doFinally(signal -> {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            Integer status = exchange.getResponse().getStatusCode() == null
                    ? null
                    : exchange.getResponse().getStatusCode().value();
            // 어느 라우트로 갔는지 함께 남긴다. 경로만으로는 어느 서비스가 처리했는지
            // 알 수 없어서, 뒤쪽 서비스 로그를 찾아갈 실마리가 된다.
            Object route = exchange.getAttribute(
                    ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR);

            if (status != null && status >= 500) {
                log.warn("{} {} {} {}ms route={}", method, path, status, elapsedMs, route);
            } else {
                log.info("{} {} {} {}ms route={}", method, path, status, elapsedMs, route);
            }
        });
    }

    /**
     * 가장 앞에 둔다. 인증에서 잘린 요청도 기록되어야 하고, 게이트웨이가 재는 시간이
     * 곧 사용자가 겪은 시간에 가장 가깝다.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
