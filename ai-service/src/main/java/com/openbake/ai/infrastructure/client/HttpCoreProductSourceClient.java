package com.openbake.ai.infrastructure.client;

import static com.openbake.common.security.service.ServiceAuthenticationHeaders.AI_SERVICE;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_NAME;
import static com.openbake.common.security.service.ServiceAuthenticationHeaders.SERVICE_TOKEN;

import com.openbake.ai.application.CoreProductSource;
import com.openbake.ai.application.port.CoreProductSourceClient;
import com.openbake.common.response.ApiResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCoreProductSourceClient implements CoreProductSourceClient {

    private static final ParameterizedTypeReference<ApiResponse<ProductSourcePage>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final String serviceToken;

    public HttpCoreProductSourceClient(
            RestClient coreRecommendationRestClient,
            @Value("${AI_SERVICE_TOKEN}") String serviceToken) {
        this.restClient = coreRecommendationRestClient;
        this.serviceToken = serviceToken;
    }

    @Override
    public ProductSourcePage fetchPage(int page, int size) {
        ApiResponse<ProductSourcePage> response = restClient.get()
                .uri(uri -> uri.path("/internal/v1/products/ids")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .header(SERVICE_NAME, AI_SERVICE)
                .header(SERVICE_TOKEN, serviceToken)
                .retrieve()
                .body(RESPONSE_TYPE);
        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalStateException("core product source response was unsuccessful");
        }
        ProductSourcePage data = response.data();
        return new ProductSourcePage(
                data.content() == null ? List.of() : data.content(),
                data.number(), data.totalPages(), data.totalElements(), data.last());
    }
}
