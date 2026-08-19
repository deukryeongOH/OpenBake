package com.openbake.product.infrastructure.elasticsearch;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerEventLogger {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @PostConstruct
    public void registerEventListeners() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("elasticsearch");

        cb.getEventPublisher()
                .onStateTransition(event ->
                        log.warn("[CircuitBreaker] 상태 전이: {} → {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onFailureRateExceeded(event ->
                        log.warn("[CircuitBreaker] 실패율 초과: {}%", event.getFailureRate()))
                .onCallNotPermitted(event ->
                        log.warn("[CircuitBreaker] 요청 차단됨 (OPEN 상태)"));
    }
}
