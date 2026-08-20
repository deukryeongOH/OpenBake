package com.openbake.ai.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.openbake.ai.application.EmbeddingFailureException;
import com.openbake.ai.application.EmbeddingProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class OpenAiEmbeddingClientTest {

    private MockRestServiceServer server;
    private OpenAiEmbeddingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.test/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new EmbeddingProperties(
                "text-embedding-3-small",
                1536,
                "product-embeddings-v1",
                "v1",
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                new EmbeddingProperties.Worker(Duration.ofSeconds(2), 20, Duration.ofMinutes(5)));
        client = new OpenAiEmbeddingClient(builder.build(), properties);
    }

    @Test
    void reads1536DimensionEmbedding() {
        String body = JsonMapper.builder().build().writeValueAsString(Map.of(
                "data", java.util.List.of(Map.of(
                        "embedding", Collections.nCopies(1536, 0.01f),
                        "index", 0))));
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var embedding = client.embed("상품 원문");

        assertThat(embedding).hasSize(1536);
        server.verify();
    }

    @Test
    void rateLimitIsRetryableAndPreservesRetryAfter() {
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "120"));

        assertThatThrownBy(() -> client.embed("상품 원문"))
                .isInstanceOf(EmbeddingFailureException.class)
                .satisfies(error -> {
                    EmbeddingFailureException failure = (EmbeddingFailureException) error;
                    assertThat(failure.isRetryable()).isTrue();
                    assertThat(failure.getErrorCode()).isEqualTo("OPENAI_RATE_LIMIT");
                    assertThat(failure.getRetryAfter()).isEqualTo(Duration.ofSeconds(120));
                });
    }

    @Test
    void authenticationFailureIsPermanent() {
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.embed("상품 원문"))
                .isInstanceOf(EmbeddingFailureException.class)
                .satisfies(error -> {
                    EmbeddingFailureException failure = (EmbeddingFailureException) error;
                    assertThat(failure.isRetryable()).isFalse();
                    assertThat(failure.getErrorCode()).isEqualTo("OPENAI_REQUEST_REJECTED");
                });
    }

    @Test
    void unexpectedVectorDimensionIsPermanent() {
        String body = "{\"data\":[{\"embedding\":[0.1,0.2],\"index\":0}]}";
        server.expect(requestTo("https://api.openai.test/v1/embeddings"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("상품 원문"))
                .isInstanceOf(EmbeddingFailureException.class)
                .satisfies(error -> {
                    EmbeddingFailureException failure = (EmbeddingFailureException) error;
                    assertThat(failure.isRetryable()).isFalse();
                    assertThat(failure.getErrorCode()).isEqualTo("INVALID_EMBEDDING_RESPONSE");
                });
    }
}
