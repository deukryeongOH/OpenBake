package com.openbake.ai.infrastructure.openai;

import com.openbake.ai.application.EmbeddingFailureException;
import com.openbake.ai.application.EmbeddingProperties;
import com.openbake.ai.application.port.EmbeddingClient;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final EmbeddingProperties properties;

    public OpenAiEmbeddingClient(RestClient openAiRestClient, EmbeddingProperties properties) {
        this.restClient = openAiRestClient;
        this.properties = properties;
    }

    @Override
    public List<Float> embed(String input) {
        EmbeddingResponse response;
        try {
            response = restClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.model(),
                            "input", input,
                            "dimensions", properties.dimensions(),
                            "encoding_format", "float"))
                    .retrieve()
                    .body(EmbeddingResponse.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw EmbeddingFailureException.transientFailure(
                    "OPENAI_RATE_LIMIT", parseRetryAfter(e), e);
        } catch (HttpClientErrorException e) {
            throw EmbeddingFailureException.permanentFailure("OPENAI_REQUEST_REJECTED", e);
        } catch (HttpServerErrorException e) {
            throw EmbeddingFailureException.transientFailure("OPENAI_SERVER_ERROR", e);
        } catch (ResourceAccessException e) {
            throw EmbeddingFailureException.transientFailure("OPENAI_NETWORK_ERROR", e);
        } catch (EmbeddingFailureException e) {
            throw e;
        } catch (Exception e) {
            throw EmbeddingFailureException.transientFailure("OPENAI_UNEXPECTED_ERROR", e);
        }

        if (response == null || response.data() == null || response.data().size() != 1
                || response.data().getFirst().embedding() == null
                || response.data().getFirst().embedding().size() != properties.dimensions()) {
            throw EmbeddingFailureException.permanentFailure("INVALID_EMBEDDING_RESPONSE", null);
        }
        return response.data().getFirst().embedding();
    }

    private Duration parseRetryAfter(HttpClientErrorException.TooManyRequests exception) {
        String value = exception.getResponseHeaders().getFirst("Retry-After");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            try {
                Duration duration = Duration.between(
                        ZonedDateTime.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
                return duration.isNegative() ? Duration.ZERO : duration;
            } catch (DateTimeParseException invalidDate) {
                return null;
            }
        }
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(List<Float> embedding, int index) {
    }
}
