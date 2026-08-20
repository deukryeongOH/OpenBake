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
import com.openbake.ai.application.MemberInteractionEventService;
import com.openbake.ai.application.MemberWithdrawnEventService;
import com.openbake.ai.application.port.EmbeddingClient;
import com.openbake.ai.domain.EmbeddingTaskStatus;
import com.openbake.ai.domain.ProductChangeType;
import com.openbake.ai.domain.ProductChangedEvent;
import com.openbake.ai.infrastructure.jpa.ConsumedEventJpaRepository;
import com.openbake.ai.infrastructure.jpa.ProductEmbeddingMetadataJpaRepository;
import com.openbake.ai.infrastructure.jpa.ProductEmbeddingTaskJpaRepository;
import com.openbake.ai.infrastructure.jpa.MemberDeletionMarkerJpaRepository;
import com.openbake.ai.infrastructure.jpa.MemberProductInteractionJpaRepository;
import com.openbake.ai.infrastructure.scheduler.EmbeddingWorker;
import com.openbake.ai.infrastructure.scheduler.InteractionRetentionScheduler;
import com.openbake.common.event.EventTopics;
import com.openbake.common.event.InteractionType;
import com.openbake.common.event.MemberInteractionEvent;
import com.openbake.common.event.MemberWithdrawnEvent;
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
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.timeout=100ms",
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
    @Autowired
    private MemberInteractionEventService interactionEventService;
    @Autowired
    private MemberWithdrawnEventService withdrawnEventService;
    @Autowired
    private MemberProductInteractionJpaRepository interactionRepository;
    @Autowired
    private MemberDeletionMarkerJpaRepository markerRepository;
    @Autowired
    private InteractionRetentionScheduler retentionScheduler;

    @MockitoBean
    private EmbeddingClient embeddingClient;

    @MockitoBean
    private EmbeddingWorker embeddingWorker;

    @BeforeEach
    void clean() {
        interactionRepository.deleteAll();
        markerRepository.deleteAll();
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

    @Test
    void interactionConsumptionIsIdempotentAndSuppressesViewsForFiveMinutes() {
        Instant base = Instant.parse("2026-08-20T01:00:00Z");
        MemberInteractionEvent first = interaction(
                InteractionType.VIEW, 101L, 201L, null, 1, null, base);

        interactionEventService.consume(first, EventTopics.PRODUCT_VIEWED, 0, 10L);
        interactionEventService.consume(first, EventTopics.PRODUCT_VIEWED, 0, 11L);
        interactionEventService.consume(interaction(
                        InteractionType.VIEW, 101L, 201L, null, 1, null,
                        base.plus(Duration.ofMinutes(4))),
                EventTopics.PRODUCT_VIEWED, 0, 12L);
        interactionEventService.consume(interaction(
                        InteractionType.VIEW, 101L, 201L, null, 1, null,
                        base.plus(Duration.ofMinutes(6))),
                EventTopics.PRODUCT_VIEWED, 0, 13L);

        assertThat(interactionRepository.findAll()).hasSize(2);
        assertThat(consumedEventRepository.findAll()).hasSize(3);
    }

    @Test
    void withdrawalHardDeletesInteractionsKeepsLatestMarkerAndBlocksDelayedEvents() {
        Instant base = Instant.parse("2026-08-20T01:00:00Z");
        interactionEventService.consume(interaction(
                        InteractionType.CART_ADD, 102L, 202L, null, 2, null, base),
                EventTopics.CART_ITEM_ADDED, 0, 20L);

        MemberWithdrawnEvent latest = withdrawn(102L, base.plus(Duration.ofHours(2)));
        withdrawnEventService.consume(latest, EventTopics.MEMBER_WITHDRAWN, 0, 21L);
        withdrawnEventService.consume(withdrawn(102L, base.plus(Duration.ofHours(1))),
                EventTopics.MEMBER_WITHDRAWN, 0, 22L);
        interactionEventService.consume(interaction(
                        InteractionType.PURCHASE, 102L, 202L, 302L, 1, 402L,
                        base.plus(Duration.ofHours(3))),
                EventTopics.ORDER_PURCHASE_CONFIRMED, 0, 23L);

        assertThat(interactionRepository.findAll()).isEmpty();
        assertThat(markerRepository.findById(102L)).get()
                .satisfies(marker -> {
                    assertThat(marker.getLatestEventId()).isEqualTo(latest.eventId());
                    assertThat(marker.getWithdrawnAt()).isEqualTo(latest.withdrawnAt());
                    assertThat(marker.getExpiresAt())
                            .isEqualTo(latest.withdrawnAt().plus(Duration.ofDays(35)));
                });
    }

    @Test
    void redisFailureDoesNotRollbackInteractionAndRetentionDeletesBoundedOldData() {
        Instant old = Instant.now().minus(Duration.ofDays(100));
        MemberInteractionEvent event = interaction(
                InteractionType.CART_ADD, 103L, 203L, null, 3, null, old);

        interactionEventService.consume(event, EventTopics.CART_ITEM_ADDED, 0, 30L);
        MemberWithdrawnEvent expiredMarker = withdrawn(104L, Instant.now().minus(Duration.ofDays(40)));
        withdrawnEventService.consume(expiredMarker, EventTopics.MEMBER_WITHDRAWN, 0, 31L);
        jdbcTemplate.update(
                "UPDATE consumed_events SET consumed_at = now() - interval '100 days' WHERE event_id = ?",
                event.eventId());

        assertThat(interactionRepository.findAll()).hasSize(1);
        retentionScheduler.clean();

        assertThat(interactionRepository.findAll()).isEmpty();
        assertThat(consumedEventRepository.findById(event.eventId())).isEmpty();
        assertThat(markerRepository.findById(104L)).isEmpty();
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

    private MemberInteractionEvent interaction(
            InteractionType type,
            Long memberId,
            Long productId,
            Long dropId,
            int quantity,
            Long orderId,
            Instant occurredAt) {
        return new MemberInteractionEvent(
                UUID.randomUUID(), 1, type, occurredAt,
                memberId, productId, dropId, quantity, orderId);
    }

    private MemberWithdrawnEvent withdrawn(Long memberId, Instant withdrawnAt) {
        return new MemberWithdrawnEvent(
                UUID.randomUUID(), 1, withdrawnAt, memberId, withdrawnAt);
    }

    private java.util.List<Float> vector() {
        return Collections.nCopies(1536, 0.01f);
    }
}
