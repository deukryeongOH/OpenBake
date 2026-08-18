package com.openbake.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    private static final String HEADER = "X-Request-Id";

    private static final Pattern SAFE_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        String incomingRequestId = exchange.getRequest()
                .getHeaders()
                .getFirst(HEADER);

        String requestId = isSafe(incomingRequestId)
                ? incomingRequestId
                : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(HEADER, requestId))
                .build();

        exchange.getResponse()
                .getHeaders()
                .set(HEADER, requestId);

        ServerWebExchange requestIdExchange = exchange.mutate()
                .request(request)
                .build();

        return chain.filter(requestIdExchange);
    }

    private boolean isSafe(String requestId) {
        return requestId != null
                && SAFE_ID.matcher(requestId).matches();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}