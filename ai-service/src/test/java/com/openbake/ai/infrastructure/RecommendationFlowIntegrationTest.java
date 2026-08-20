package com.openbake.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.openbake.ai.application.EmbeddingTaskProcessor;
import com.openbake.ai.application.RecommendationResult;
import com.openbake.ai.application.RecommendationService;
import com.openbake.ai.application.RecommendationUnavailableException;
import com.openbake.ai.application.port.ProductEmbeddingIndex;
import com.openbake.ai.application.port.ProductEmbeddingIndex.ProductEmbeddingIndexDocument;
import com.openbake.ai.domain.MemberProductInteraction;
import com.openbake.ai.domain.RecommendationStrategy;
import com.openbake.ai.infrastructure.jpa.MemberProductInteractionJpaRepository;
import com.openbake.ai.infrastructure.scheduler.EmbeddingWorker;
import com.openbake.common.event.InteractionType;
import com.openbake.common.event.MemberInteractionEvent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
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
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false",
        "openbake.ai.recommendation.cache-ttl=PT0.5S"
})
class RecommendationFlowIntegrationTest {

    private static final String SERVICE_TOKEN = UUID.randomUUID().toString();
    private static final AtomicReference<List<Card>> VALIDATION_CARDS =
            new AtomicReference<>(List.of());
    private static final AtomicReference<List<Card>> LATEST_CARDS =
            new AtomicReference<>(List.of(new Card(999L, "latest")));
    private static final AtomicBoolean CORE_FAILURE = new AtomicBoolean();
    private static final HttpServer CORE_SERVER = startCoreServer();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.9.0")
            .withCreateContainerCmdModifier(command -> command.withHostName("kafka-recommendation-test"))
            .withEnv(
                    "KAFKA_LISTENERS",
                    "PLAINTEXT://kafka-recommendation-test:9092,BROKER://kafka-recommendation-test:9093,CONTROLLER://localhost:9094");

    @Container
    @ServiceConnection
    static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:9.0.3")
            .withEnv("xpack.security.enabled", "false")
            .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("CORE_SERVICE_URL", () -> "http://127.0.0.1:" + CORE_SERVER.getAddress().getPort());
        registry.add("AI_SERVICE_TOKEN", () -> SERVICE_TOKEN);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private RecommendationService recommendationService;
    @Autowired
    private MemberProductInteractionJpaRepository interactionRepository;
    @Autowired
    private ProductEmbeddingIndex embeddingIndex;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private EmbeddingWorker embeddingWorker;
    @MockitoBean
    private EmbeddingTaskProcessor embeddingTaskProcessor;

    @BeforeEach
    void clean() {
        CORE_FAILURE.set(false);
        VALIDATION_CARDS.set(List.of());
        LATEST_CARDS.set(List.of(new Card(999L, "latest")));
        interactionRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        var indexOperations = elasticsearchOperations.indexOps(
                IndexCoordinates.of("product-embeddings-v1"));
        if (indexOperations.exists()) {
            indexOperations.delete();
        }
    }

    @AfterAll
    static void stopCoreServer() {
        CORE_SERVER.stop(0);
    }

    @Test
    void endToEndStrategiesCacheAndFailureIsolation() {
        Instant now = Instant.now();
        index(201L, "DROP", "MEAL_BREADS", vector(0));
        index(101L, "GENERAL", "MEAL_BREADS", vector(0));
        index(102L, "DROP", "MEAL_BREADS", vector(0));
        refreshIndex();
        interactionRepository.save(interaction(1L, 201L, InteractionType.PURCHASE, 2, null, now));
        VALIDATION_CARDS.set(List.of(new Card(101L, "personalized")));

        RecommendationResult personalized = recommendationService.recommend(1L, 1);

        assertThat(personalized.strategy()).isEqualTo(RecommendationStrategy.PERSONALIZED);
        assertThat(personalized.items()).extracting(RecommendationResult.Item::productId)
                .containsExactly(101L);
        assertThat(personalized.items()).noneMatch(item -> item.productId().equals(102L));
        assertThat(redisTemplate.hasKey("ai:recommendation:v1:1")).isTrue();

        interactionRepository.deleteAll();
        elasticsearchOperations.indexOps(IndexCoordinates.of("product-embeddings-v1")).delete();
        assertThat(recommendationService.recommend(1L, 1).strategy())
                .isEqualTo(RecommendationStrategy.PERSONALIZED);

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(redisTemplate.hasKey("ai:recommendation:v1:1")).isFalse());
        VALIDATION_CARDS.set(List.of(new Card(999L, "latest")));
        assertThat(recommendationService.recommend(1L, 1).strategy())
                .isEqualTo(RecommendationStrategy.LATEST);

        interactionRepository.save(interaction(
                6L, 201L, InteractionType.VIEW, 1, null, Instant.now()));
        assertThat(recommendationService.recommend(6L, 1).strategy())
                .isEqualTo(RecommendationStrategy.LATEST);
        interactionRepository.deleteAll();

        index(103L, "GENERAL", "CAKES_TARTS", vector(1));
        refreshIndex();
        interactionRepository.save(interaction(50L, 103L, InteractionType.PURCHASE, 4, null, now));
        VALIDATION_CARDS.set(List.of(new Card(103L, "popular")));
        RecommendationResult popular = recommendationService.recommend(2L, 1);
        assertThat(popular.strategy()).isEqualTo(RecommendationStrategy.POPULAR);
        assertThat(popular.items()).extracting(RecommendationResult.Item::productId)
                .containsExactly(103L);

        interactionRepository.save(interaction(
                7L, 103L, InteractionType.VIEW, 1, null, Instant.now()));
        VALIDATION_CARDS.set(List.of());
        RecommendationResult allFiltered = recommendationService.recommend(7L, 1);
        assertThat(allFiltered.strategy()).isEqualTo(RecommendationStrategy.PERSONALIZED);
        assertThat(allFiltered.items()).isEmpty();

        interactionRepository.deleteAll();
        VALIDATION_CARDS.set(List.of(new Card(999L, "latest")));
        RecommendationResult latest = recommendationService.recommend(3L, 1);
        assertThat(latest.strategy()).isEqualTo(RecommendationStrategy.LATEST);
        assertThat(latest.items()).extracting(RecommendationResult.Item::productId)
                .containsExactly(999L);

        redis.stop();
        assertThat(recommendationService.recommend(4L, 1).strategy())
                .isEqualTo(RecommendationStrategy.LATEST);

        CORE_FAILURE.set(true);
        assertThatThrownBy(() -> recommendationService.recommend(5L, 1))
                .isInstanceOf(RecommendationUnavailableException.class);
    }

    private void index(Long productId, String type, String category, List<Float> vector) {
        Instant now = Instant.now();
        embeddingIndex.upsert(new ProductEmbeddingIndexDocument(
                productId, "product-" + productId, "description", category, type,
                vector, "hash-" + productId, "test-model", "v1", now, now));
    }

    private void refreshIndex() {
        elasticsearchOperations.indexOps(IndexCoordinates.of("product-embeddings-v1")).refresh();
    }

    private MemberProductInteraction interaction(
            Long memberId,
            Long productId,
            InteractionType type,
            int quantity,
            Long dropId,
            Instant occurredAt) {
        return MemberProductInteraction.from(new MemberInteractionEvent(
                UUID.randomUUID(), 1, type, occurredAt, memberId, productId, dropId,
                quantity, type == InteractionType.PURCHASE ? 1L : null),
                occurredAt);
    }

    private List<Float> vector(int hotIndex) {
        List<Float> vector = new ArrayList<>(java.util.Collections.nCopies(1536, 0.0f));
        vector.set(hotIndex, 1.0f);
        return List.copyOf(vector);
    }

    private static HttpServer startCoreServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/v1/products/recommendation-candidates",
                    exchange -> respond(exchange, VALIDATION_CARDS.get()));
            server.createContext("/internal/v1/products/latest-recommendation-candidates",
                    exchange -> respond(exchange, LATEST_CARDS.get()));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(HttpExchange exchange, List<Card> cards) throws IOException {
        if (CORE_FAILURE.get()) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        if (!SERVICE_TOKEN.equals(exchange.getRequestHeaders().getFirst("X-Openbake-Service-Token"))
                || !"ai-service".equals(exchange.getRequestHeaders().getFirst("X-Openbake-Service-Name"))) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
        }
        String products = cards.stream().map(Card::json).collect(java.util.stream.Collectors.joining(","));
        byte[] body = ("{\"success\":true,\"data\":{\"products\":[" + products + "]}}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private record Card(Long productId, String name) {
        private String json() {
            return "{\"productId\":" + productId
                    + ",\"name\":\"" + name
                    + "\",\"imageUrl\":\"https://example.test/" + productId
                    + "\",\"price\":1000,\"category\":\"MEAL_BREADS\",\"remainQuantity\":10}";
        }
    }
}
