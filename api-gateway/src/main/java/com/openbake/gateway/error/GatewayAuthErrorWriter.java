package com.openbake.gateway.error;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class GatewayAuthErrorWriter {

    private static final String REQUEST_ID_HEADER =
            "X-Request-Id";

    private final ObjectMapper objectMapper;

    public GatewayAuthErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> unauthorized(
            ServerWebExchange exchange,
            String code
    ) {
        String message = switch (code) {
            case "AUTHENTICATION_REQUIRED" ->
                    "인증이 필요합니다.";
            case "TOKEN_EXPIRED" ->
                    "인증 토큰이 만료되었습니다.";
            case "TOKEN_REVOKED" ->
                    "사용할 수 없는 인증 토큰입니다.";
            case "TOKEN_INVALID", "TOKEN_CLAIMS_INVALID" ->
                    "유효하지 않은 인증 토큰입니다.";
            default ->
                    "인증에 실패했습니다.";
        };

        return write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                code,
                message
        );
    }

    public Mono<Void> authenticationServiceUnavailable(
            ServerWebExchange exchange
    ) {
        return write(
                exchange,
                HttpStatus.SERVICE_UNAVAILABLE,
                "AUTHENTICATION_SERVICE_UNAVAILABLE",
                "인증 서비스를 일시적으로 사용할 수 없습니다."
        );
    }

    private Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message
    ) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        String requestId = exchange.getResponse()
                .getHeaders()
                .getFirst(REQUEST_ID_HEADER);

        GatewayAuthErrorResponse body =
                new GatewayAuthErrorResponse(
                        false,
                        new GatewayAuthError(code, message),
                        requestId
                );

        byte[] bytes;

        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JacksonException exception) {
            return Mono.error(exception);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(
                new MediaType(
                        MediaType.APPLICATION_JSON,
                        StandardCharsets.UTF_8
                )
        );

        return exchange.getResponse().writeWith(
                Mono.just(
                        exchange.getResponse()
                                .bufferFactory()
                                .wrap(bytes)
                )
        );
    }

    private record GatewayAuthErrorResponse(
            boolean success,
            GatewayAuthError error,
            String requestId
    ) {
    }

    private record GatewayAuthError(
            String code,
            String message
    ) {
    }
}