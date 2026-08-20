package com.openbake.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.openbake.ai.application.EmbeddingTaskClaimer;
import com.openbake.ai.application.EmbeddingTaskProcessor;
import com.openbake.ai.application.ProductChangedEventService;
import com.openbake.ai.application.port.EmbeddingClient;
import com.openbake.ai.domain.EmbeddingTaskStatus;
import com.openbake.ai.domain.ProductChangeType;
import com.openbake.ai.domain.ProductChangedEvent;
import com.openbake.ai.infrastructure.jpa.ConsumedEventJpaRepository;
import com.openbake.ai.infrastructure.jpa.ProductEmbeddingMetadataJpaRepository;
import com.openbake.ai.infrastructure.jpa.ProductEmbeddingTaskJpaRepository;
import com.openbake.ai.infrastructure.scheduler.EmbeddingWorker;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "OPENAI_API_KEY=test-key",
        "AI_DB_URL=jdbc:postgresql://unused:5432/unused",
        "DB_USERNAME=unused",
        "DB_PASSWORD=unused",
        "KAFKA_BOOTSTRAP_SERVERS=unused:9092",
        "spring.task.scheduling.enabled=false"
})
class ProductEmbeddingPipelineIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    @ServiceConnection
    // Kafka 3.9는 storage 초기화 시 0.0.0.0 advertised listener를 거부하므로 routable hostname을 사용한다.
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.9.0")
            .withCreateContainerCmdModifier(command -> command.withHostName("kafka-test"))
            .withEnv(
                    "KAFKA_LISTENERS",
                    "PLAINTEXT://kafka-test:9092,BROKER://kafka-test:9093,CONTROLLER://localhost:9094");

    @Container
    @ServiceConnection
    static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:9.0.3")
            .withEnv("xpack.security.enabled", "false")
            .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private ProductChangedEventService eventService;
    @Autowired
    private ProductEmbeddingTaskJpaRepository taskRepository;
    @Autowired
    private ProductEmbeddingMetadataJpaRepository metadataRepository;
    @Autowired
    private ConsumedEventJpaRepository consumedEventRepository;
    @Autowired
    private EmbeddingTaskProcessor taskProcessor;
    @Autowired
    private EmbeddingTaskClaimer taskClaimer;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmbeddingClient embeddingClient;

    @MockitoBean
    private EmbeddingWorker embeddingWorker;

    @BeforeEach
    void clean() {
        consumedEventRepository.deleteAll();
        metadataRepository.deleteAll();
        taskRepository.deleteAll();
        var indexOperations = elasticsearchOperations.indexOps(IndexCoordinates.of("product-embeddings-v1"));
        if (indexOperations.exists()) {
            indexOperations.delete();
        }
    }

    @Test
    void kafkaConsumerPersistsConsumedEventAndPendingTaskAtomically() {
        ProductChangedEvent event = changed(10L, ProductChangeType.CREATED, "통밀 식빵", "담백한 식사빵");

        kafkaTemplate.send("product.changed.v1", "10", objectMapper.writeValueAsString(event)).join();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(consumedEventRepository.existsById(event.eventId())).isTrue();
            assertThat(taskRepository.findAll())
                    .singleElement()
                    .satisfies(task -> {
                        assertThat(task.getProductId()).isEqualTo(10L);
                        assertThat(task.getStatus()).isEqualTo(EmbeddingTaskStatus.PENDING);
                    });
        });
    }

    @Test
    void workerCreatesMappingUpsertsReusesHashAndDeletesDocument() {
        given(embeddingClient.embed(anyString())).willReturn(vector());
        ProductChangedEvent created = changed(20L, ProductChangeType.CREATED, "소금빵", "버터 풍미");
        eventService.consume(created, "product.changed.v1", 0, 1L);

        assertThat(taskProcessor.processNext()).isTrue();

        assertThat(metadataRepository.findById(20L)).isPresent();
        assertThat(elasticsearchOperations.exists(
                "20", IndexCoordinates.of("product-embeddings-v1"))).isTrue();
        var mapping = elasticsearchOperations.indexOps(IndexCoordinates.of("product-embeddings-v1")).getMapping();
        @SuppressWarnings("unchecked")
        var embeddingMapping = (java.util.Map<String, Object>)
                ((java.util.Map<String, Object>) mapping.get("properties")).get("embedding");
        assertThat(embeddingMapping.get("dims")).isEqualTo(1536);
        assertThat(embeddingMapping.get("similarity")).isEqualTo("cosine");
        assertThat(taskRepository.findAll()).singleElement()
                .extracting("status").isEqualTo(EmbeddingTaskStatus.COMPLETED);

        ProductChangedEvent sameSource = changed(20L, ProductChangeType.UPDATED, "소금빵", "버터 풍미");
        eventService.consume(sameSource, "product.changed.v1", 0, 2L);
        assertThat(taskProcessor.processNext()).isTrue();
        verify(embeddingClient, times(1)).embed(anyString());

        ProductChangedEvent deleted = new ProductChangedEvent(
                UUID.randomUUID(), 1, ProductChangeType.DELETED, Instant.now().plusSeconds(1),
                20L, null, null, null, null);
        eventService.consume(deleted, "product.changed.v1", 0, 3L);
        assertThat(taskProcessor.processNext()).isTrue();

        assertThat(metadataRepository.findById(20L)).isEmpty();
        assertThat(elasticsearchOperations.exists(
                "20", IndexCoordinates.of("product-embeddings-v1"))).isFalse();
    }

    @Test
    void expiredProcessingLeaseCanBeReclaimed() {
        ProductChangedEvent event = changed(30L, ProductChangeType.CREATED, "크루아상", "바삭한 결");
        eventService.consume(event, "product.changed.v1", 0, 1L);

        assertThat(taskClaimer.claimNext()).isPresent();
        assertThat(taskClaimer.claimNext()).isEmpty();
        jdbcTemplate.update(
                "UPDATE product_embedding_tasks SET lease_expires_at = now() - interval '1 second' WHERE product_id = ?",
                30L);

        assertThat(taskClaimer.claimNext()).isPresent();
    }

    private ProductChangedEvent changed(
            Long productId, ProductChangeType type, String name, String description) {
        return new ProductChangedEvent(
                UUID.randomUUID(),
                1,
                type,
                Instant.now(),
                productId,
                name,
                description,
                "MEAL_BREADS",
                "GENERAL");
    }

    private java.util.List<Float> vector() {
        return Collections.nCopies(1536, 0.01f);
    }
}
