package com.openbake.ai.infrastructure.client;

import static com.openbake.common.security.service.ServiceAuthenticationHeaders.AI_SERVICE;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_NAME;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_TOKEN;

import com.openbake.ai.application.CoreProductCard;
import com.openbake.ai.application.RecommendationUnavailableException;
import com.openbake.ai.application.port.CoreRecommendationClient;
import com.openbake.common.response.ApiResponse;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCoreRecommendationClient implements CoreRecommendationClient {

    private static final ParameterizedTypeReference<ApiResponse<ProductsResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String serviceToken;

    public HttpCoreRecommendationClient(
            RestClient coreRecommendationRestClient,
            @Value("${AI_SERVICE_TOKEN}") String serviceToken) {
        this.restClient = coreRecommendationRestClient;
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalArgumentException("AI service token must be configured");
        }
        this.serviceToken = serviceToken;
    }

    @Override
    public List<CoreProductCard> validate(Long memberId, List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        try {
            ApiResponse<ProductsResponse> response = restClient.post()
                    .uri("/internal/v1/products/recommendation-candidates")
                    .header(SERVICE_NAME, AI_SERVICE)
                    .header(SERVICE_TOKEN, serviceToken)
                    .body(new CandidateRequest(memberId, productIds))
                    .retrieve()
                    .body(RESPONSE_TYPE);
            return products(response);
        } catch (RuntimeException exception) {
            throw new RecommendationUnavailableException(exception);
        }
    }

    @Override
    public List<CoreProductCard> latest(Long memberId, int size) {
        try {
            ApiResponse<ProductsResponse> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/products/latest-recommendation-candidates")
                            .queryParam("memberId", memberId)
                            .queryParam("size", size)
                            .build())
                    .header(SERVICE_NAME, AI_SERVICE)
                    .header(SERVICE_TOKEN, serviceToken)
                    .retrieve()
                    .body(RESPONSE_TYPE);
            return products(response);
        } catch (RuntimeException exception) {
            if (exception instanceof RecommendationUnavailableException unavailable) {
                throw unavailable;
            }
            throw new RecommendationUnavailableException(exception);
        }
    }

    private List<CoreProductCard> products(ApiResponse<ProductsResponse> response) {
        if (response == null || !response.success() || response.data() == null) {
            throw new RecommendationUnavailableException(
                    new IllegalStateException("core recommendation response was unsuccessful"));
        }
        return Objects.requireNonNullElse(response.data().products(), List.of());
    }

    private record CandidateRequest(Long memberId, List<Long> productIds) {
    }

    private record ProductsResponse(List<CoreProductCard> products) {
    }
}
