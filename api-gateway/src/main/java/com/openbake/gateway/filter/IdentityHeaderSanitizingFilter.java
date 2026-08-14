package com.openbake.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class IdentityHeaderSanitizingFilter implements GlobalFilter, Ordered {

    static final List<String> INTERNAL_HEADERS = List.of(
            "X-Openbake-Member-ID",
            "X-Openbake-Member-Role",
            "X-Openbake-Auth-Source"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = exchange.getRequest()
                .mutate()
                .headers(headers ->
                        INTERNAL_HEADERS.forEach(headers::remove))
                .build();

        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(sanitizedRequest)
                .build();

        return chain.filter(sanitizedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
