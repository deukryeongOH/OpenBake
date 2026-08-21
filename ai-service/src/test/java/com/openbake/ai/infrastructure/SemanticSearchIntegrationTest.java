package com.openbake.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.openbake.ai.application.SemanticSearchRequest;
import com.openbake.ai.application.SemanticSearchResult;
import com.openbake.ai.application.SemanticSearchResult.Item;
import com.openbake.ai.application.SemanticSearchService;
import com.openbake.ai.application.port.EmbeddingClient;
import com.openbake.ai.application.port.ProductEmbeddingIndex;
import com.openbake.ai.application.port.ProductEmbeddingIndex.ProductEmbeddingIndexDocument;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "OPENAI_API_KEY=test-key",
        "AI_DB_URL=jdbc:postgresql://unused:5432/unused",
        "DB_USERNAME=unused",
        "DB_PASSWORD=unused",
        "KAFKA_BOOTSTRAP_SERVERS=unused:9092",
        "CORE_SERVICE_URL=http://127.0.0.1:1",
        "AI_SERVICE_TOKEN=${random.uuid}",
        "CORE_SERVICE_TOKEN=${random.uuid}",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.timeout=100ms",
        "spring.task.scheduling.enabled=false"
})
class SemanticSearchIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.9.0")
            .withCreateContainerCmdModifier(command -> command.withHostName("kafka-semantic-search-test"))
            .withEnv(
                    "KAFKA_LISTENERS",
                    "PLAINTEXT://kafka-semantic-search-test:9092,BROKER://kafka-semantic-search-test:9093,CONTROLLER://localhost:9094");

    @Container
    @ServiceConnection
    static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:9.0.3")
            .withEnv("xpack.security.enabled", "false")
            .withStartupTimeout(Duration.ofMinutes(2));

    @org.springframework.test.context.DynamicPropertySource
    static void properties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private SemanticSearchService semanticSearchService;
    @Autowired
    private ProductEmbeddingIndex embeddingIndex;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @MockitoBean
    private EmbeddingClient embeddingClient;

    @BeforeEach
    void clean() {
        var indexOperations = elasticsearchOperations.indexOps(
                IndexCoordinates.of("product-embeddings-v1"));
        if (indexOperations.exists()) {
            indexOperations.delete();
        }
    }

    @Test
    void dropProductsAreExcludedFromCandidates() {
        index(1L, "GENERAL", "MEAL_BREADS", vector(0));
        index(2L, "DROP", "MEAL_BREADS", vector(0));
        refresh();
        given(embeddingClient.embed(any())).willReturn(vector(0));

        SemanticSearchResult result = semanticSearchService.search(
                new SemanticSearchRequest("빵", null, null));

        assertThat(result.items()).extracting(Item::productId).containsExactly(1L);
    }

    @Test
    void categoryFilterNarrowsCandidatesToThatCategoryOnly() {
        index(1L, "GENERAL", "MEAL_BREADS", vector(0));
        index(2L, "GENERAL", "CAKES_TARTS", vector(0));
        refresh();
        given(embeddingClient.embed(any())).willReturn(vector(0));

        SemanticSearchResult result = semanticSearchService.search(
                new SemanticSearchRequest("빵", null, "CAKES_TARTS"));

        assertThat(result.items()).extracting(Item::productId).containsExactly(2L);
    }

    @Test
    void rankStartsAtOneAndOrdersByCosineSimilarity() {
        index(1L, "GENERAL", "MEAL_BREADS", vector(0));
        index(2L, "GENERAL", "MEAL_BREADS", nearVector(0));
        refresh();
        given(embeddingClient.embed(any())).willReturn(vector(0));

        SemanticSearchResult result = semanticSearchService.search(
                new SemanticSearchRequest("빵", null, null));

        assertThat(result.items()).extracting(Item::rank).containsExactly(1, 2);
        assertThat(result.items().getFirst().productId()).isEqualTo(1L);
    }

    private void index(Long productId, String type, String category, List<Float> vector) {
        Instant now = Instant.now();
        embeddingIndex.upsert(new ProductEmbeddingIndexDocument(
                productId, "product-" + productId, "description", category, type,
                vector, "hash-" + productId, "test-model", "v1", now, now));
    }

    private void refresh() {
        elasticsearchOperations.indexOps(IndexCoordinates.of("product-embeddings-v1")).refresh();
    }

    private List<Float> vector(int hotIndex) {
        List<Float> vector = new ArrayList<>(Collections.nCopies(1536, 0.0f));
        vector.set(hotIndex, 1.0f);
        return List.copyOf(vector);
    }

    private List<Float> nearVector(int hotIndex) {
        List<Float> vector = new ArrayList<>(Collections.nCopies(1536, 0.0f));
        vector.set(hotIndex, 0.9f);
        vector.set(hotIndex + 1, 0.1f);
        return List.copyOf(vector);
    }
}
