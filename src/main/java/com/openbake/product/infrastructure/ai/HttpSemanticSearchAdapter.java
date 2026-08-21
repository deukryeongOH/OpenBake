package com.openbake.product.infrastructure.ai;

import static com.openbake.common.security.service.ServiceAuthenticationHeaders.CORE_SERVICE;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_NAME;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_TOKEN;

import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.service.CoreServicePaths;
import com.openbake.product.application.port.SemanticSearchPort;
import com.openbake.product.domain.Category;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class HttpSemanticSearchAdapter implements SemanticSearchPort {

    private static final ParameterizedTypeReference<ApiResponse<SemanticSearchResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String serviceToken;
    private final Counter failureCounter;

    public HttpSemanticSearchAdapter(
            RestClient aiServiceSearchRestClient,
            @Value("${CORE_SERVICE_TOKEN}") String serviceToken,
            MeterRegistry meterRegistry) {
        this.restClient = aiServiceSearchRestClient;
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalArgumentException("Core service token must be configured");
        }
        this.serviceToken = serviceToken;
        this.failureCounter = meterRegistry.counter("openbake.search.semantic.failures");
    }

    @Override
    @CircuitBreaker(name = "ai-semantic-search", fallbackMethod = "fallback")
    public List<SemanticCandidate> findNearest(String keyword, Category category, int size) {
        ApiResponse<SemanticSearchResponse> response = restClient.post()
                .uri(CoreServicePaths.SEMANTIC_SEARCH)
                .header(SERVICE_NAME, CORE_SERVICE)
                .header(SERVICE_TOKEN, serviceToken)
                .body(new SemanticSearchApiRequest(keyword, size, category == null ? null : category.name()))
                .retrieve()
                .body(RESPONSE_TYPE);
        return items(response);
    }

    @SuppressWarnings("unused")
    private List<SemanticCandidate> fallback(String keyword, Category category, int size, Throwable exception) {
        log.warn("의미 검색 실패 — 키워드 결과만으로 응답. reason={}", exception.getMessage());
        failureCounter.increment();
        return List.of();
    }

    private List<SemanticCandidate> items(ApiResponse<SemanticSearchResponse> response) {
        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("의미 검색 응답이 비어있습니다.");
        }
        return Objects.requireNonNullElse(response.data().items(), List.of());
    }

    private record SemanticSearchApiRequest(String query, int size, String category) {
    }

    private record SemanticSearchResponse(List<SemanticCandidate> items) {
    }
}
