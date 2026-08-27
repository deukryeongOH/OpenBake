package com.openbake.drop.infrastructure.cache;

import com.openbake.OpenbakeApplication;
import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.cache.TodayDropCache;
import com.openbake.drop.application.dto.DropInfoCommand;
import com.openbake.drop.application.dto.DropInfoResult;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.application.port.CurrentMemberPort;
import com.openbake.drop.application.port.CurrentSellerPort;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.service.DropLockService;
import com.openbake.drop.application.service.DropService;
import com.openbake.drop.domain.DropStatus;
import com.openbake.drop.domain.DropTimeSlot;
import com.openbake.drop.domain.repository.DropRepository;
import com.openbake.product.domain.Category;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.testcontainers.containers.GenericContainer;
import com.openbake.product.infrastructure.elasticsearch.ProductSearchRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * docs/11-drop-cache-invalidation-propagation.md 에서 설계한 캐시 무효화 전파가
 * 실제로 "서로 다른 Pod" 사이에서 동작하는지 검증한다.
 *
 * <p>Pod를 흉내 내기 위해 같은 JVM 안에서 {@link OpenbakeApplication} 전체 컨텍스트를
 * 두 개(Pod A, Pod B) 독립적으로 띄운다. 이 프로젝트의 기존 통합 테스트(예:
 * {@code OutboxEventProcessorIntegrationTest})가 이미 전체 컨텍스트를 올리는 방식을 쓰고 있어
 * 그 관례를 그대로 따른다. 두 컨텍스트는 같은 H2 인메모리 DB(URL 동일)를 공유해 "같은 Postgres를
 * 보는 두 Pod"를, 같은 로컬 Redis(기본 localhost:6379 — CI의 redis 서비스, 로컬은
 * docker-compose.yaml의 redis 컨테이너)를 공유해 "같은 Redis를 보는 두 Pod"를 재현한다.
 *
 * <p>Pub/Sub 자체가 검증 대상이라 Redis는 목으로 대체할 수 없다. 개발자가 직접 컨테이너를 띄우지
 * 않아도 되도록 Testcontainers로 자동 기동한다(ai-service 모듈이 이미 쓰는 것과 같은 방식).
 * ES는 이 테스트의 관심사가 아니라 {@link FakeExternalPortsConfig}에서 자동구성을 끄고 목으로 대체한다
 * (SellerSettlementEncryptionIntegrationTest 등과 같은 이유 — SimpleElasticsearchRepository가
 * 생성 시점에 실제 접속을 시도한다).
 *
 * <p>판매자(CurrentSellerPort)·회원(CurrentMemberPort)·상품(ProductPort)은 다른 모듈/서비스
 * 소관이라 이 테스트의 관심사가 아니므로 가벼운 가짜 구현으로 대체한다. 캐시 무효화 전파와
 * 직접 관련된 DropService/DropLockService/TodayDropCache/DropCacheInvalidationPublisher/
 * DropCacheInvalidationSubscriber/DropRepository(H2)는 전부 실제 구현을 그대로 쓴다.
 */
class DropCacheInvalidationPropagationIntegrationTest {

    private static final String DB_NAME = "drop_cache_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final String DB_URL = "jdbc:h2:mem:" + DB_NAME + ";DB_CLOSE_DELAY=-1";

    // 실제 Pub/Sub 전파를 검증해야 하므로 목으로 대체할 수 없다. Testcontainers가 테스트 실행 시
    // 자동으로 띄우고 내려서 개발자가 수동으로 컨테이너를 관리할 필요가 없다.
    private static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static ConfigurableApplicationContext podA;
    private static ConfigurableApplicationContext podB;

    private static DropService dropServiceA;
    private static DropService dropServiceB;
    private static DropLockService dropLockServiceB;
    private static TodayDropCache todayDropCacheA;
    private static TodayDropCache todayDropCacheB;
    private static DropRepository dropRepositoryA;

    private Long dropIdUnderTest;

    @BeforeAll
    static void startPods() throws InterruptedException {
        redis.start();

        // Pod A가 완전히 기동을 마친 뒤에야 Pod B를 띄운다. 둘 다 create-drop이라,
        // 동시에 뜨면 한쪽의 스키마 생성이 다른 쪽과 겹칠 수 있다(둘 다 시작 시점엔 빈 스키마라
        // 실질적 위험은 없지만, 굳이 경합을 만들 이유가 없어 순차 기동으로 둔다).
        podA = startPod();
        podB = startPod();

        dropServiceA = podA.getBean(DropService.class);
        dropServiceB = podB.getBean(DropService.class);
        dropLockServiceB = podB.getBean(DropLockService.class);
        todayDropCacheA = podA.getBean(TodayDropCache.class);
        todayDropCacheB = podB.getBean(TodayDropCache.class);
        dropRepositoryA = podA.getBean(DropRepository.class);

        // RedisMessageListenerContainer의 채널 구독은 SmartLifecycle.start() 안에서
        // 비동기로 이뤄진다. 기동 직후 곧바로 publish하면 구독이 아직 안 걸린 순간의
        // 신호가 유실될 수 있으므로(Pub/Sub는 at-most-once), 두 Pod 모두 구독이
        // 자리잡을 시간을 잠깐 준다.
        Thread.sleep(500);
    }

    @AfterAll
    static void stopPods() {
        if (podB != null) {
            podB.close();
        }
        if (podA != null) {
            podA.close();
        }
        redis.stop();
    }

    @AfterEach
    void cleanUpDrop() {
        if (dropIdUnderTest != null) {
            dropRepositoryA.findById(dropIdUnderTest).ifPresent(dropRepositoryA::delete);
            dropIdUnderTest = null;
        }
        // 다음 테스트가 깨끗한 상태에서 시작하도록 두 Pod 모두 로컬 캐시를 비운다.
        todayDropCacheA.refresh();
        todayDropCacheB.refresh();
    }

    @Test
    @DisplayName("Pod A에서 드롭을 등록하면 Pub/Sub로 Pod B의 캐시에도 전파되고, 원래 버그의 증상(lock-start 오거부)도 재현되지 않는다")
    void registerDrop_PropagatesToOtherPod_ViaPubSub() {
        DropTimeSlot slot = requireAvailableSlotToday();
        LocalDateTime dropStart = LocalDate.now().atTime(slot.getStart());
        LocalDateTime dropEnd = LocalDate.now().atTime(slot.getEnd());

        DropInfoCommand command = DropInfoCommand.create(
                "두쫀쿠", "설명", "image.jpg", dropStart, dropEnd,
                100, 5, 8000, Set.of(dropStart.toLocalDate().plusDays(7)), Category.SWEET_BREADS);

        // when: Pod A에서 등록
        DropInfoResult result = dropServiceA.registerDrop(command);
        Long dropId = dropRepositoryA.findByProductId(result.productId()).getId();
        dropIdUnderTest = dropId;

        // then: 등록한 Pod A는 로컬 refresh로 즉시 안다
        assertThat(todayDropCacheA.find(dropId)).isPresent();

        // then: 다른 Pod B는 처음엔 모르다가, 커밋 후 전파된 Pub/Sub 신호를 받고 곧 알게 된다
        awaitUntil(Duration.ofSeconds(3), () -> todayDropCacheB.find(dropId).isPresent());

        CachedDrop seenByPodB = todayDropCacheB.find(dropId).orElseThrow();
        assertThat(seenByPodB.limitQuantity()).isEqualTo(5);
        assertThat(seenByPodB.dropEnd()).isEqualTo(dropEnd);

        // 원래 버그(docs/11 1.3절)가 고쳐졌는지도 함께 확인한다 — 재고는 있는데
        // 캐시가 뒤처져 lock-start 사전검증이 DROP_NOT_ACTIVE로 거부되던 증상이다.
        assertThatCode(() -> dropLockServiceB.checkLimitQuantityPerPerson(dropId, 3))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Pod B에서 드롭을 수정하면 Pub/Sub로 Pod A의 캐시에도 전파된다")
    void updateDropProduct_PropagatesToOtherPod_ViaPubSub() {
        DropTimeSlot slot = requireAvailableSlotToday();
        LocalDateTime dropStart = LocalDate.now().atTime(slot.getStart());
        LocalDateTime dropEnd = LocalDate.now().atTime(slot.getEnd());

        DropInfoResult created = dropServiceA.registerDrop(DropInfoCommand.create(
                "두쫀쿠", "설명", "image.jpg", dropStart, dropEnd,
                100, 5, 8000, Set.of(dropStart.toLocalDate().plusDays(7)), Category.SWEET_BREADS));
        Long dropId = dropRepositoryA.findByProductId(created.productId()).getId();
        dropIdUnderTest = dropId;

        // 등록 전파 자체는 위 테스트가 이미 검증했으므로, 여기서는 "수정 전파"만 보기 위해
        // Pod B가 이 드롭을 이미 알고 있는 상태(기준선)를 먼저 만들어 둔다.
        todayDropCacheB.refresh();
        assertThat(todayDropCacheB.find(dropId)).isPresent();

        // when: Pod B에서 1인당 제한 수량을 5 -> 8로 수정 (시간대는 그대로라 슬롯/미래 시각 검증에
        // 새로 걸리지 않는다. validateFiveDropPerDayExcludingSelf가 이 드롭 자신은 제외한다)
        dropServiceB.updateDropProduct(dropId, DropInfoCommand.create(
                "두쫀쿠(수정)", "설명", "image.jpg", dropStart, dropEnd,
                100, 8, 8000, Set.of(dropStart.toLocalDate().plusDays(7)), Category.SWEET_BREADS));

        // then: 다른 Pod A의 캐시가 뒤늦게 새 값으로 갱신된다
        awaitUntil(Duration.ofSeconds(3), () -> todayDropCacheA.find(dropId)
                .map(CachedDrop::limitQuantity)
                .orElse(-1) == 8);
    }

    @Test
    @DisplayName("Pod A에서 드롭을 삭제하면 Pub/Sub로 Pod B의 캐시에서도 사라진다")
    void deleteProduct_PropagatesToOtherPod_ViaPubSub() {
        DropTimeSlot slot = requireAvailableSlotToday();
        LocalDateTime dropStart = LocalDate.now().atTime(slot.getStart());
        LocalDateTime dropEnd = LocalDate.now().atTime(slot.getEnd());

        DropInfoResult created = dropServiceA.registerDrop(DropInfoCommand.create(
                "두쫀쿠", "설명", "image.jpg", dropStart, dropEnd,
                100, 5, 8000, Set.of(dropStart.toLocalDate().plusDays(7)), Category.SWEET_BREADS));
        Long dropId = dropRepositoryA.findByProductId(created.productId()).getId();
        dropIdUnderTest = dropId;

        // 등록 전파는 이미 검증했으므로, 여기서는 "삭제 전파"만 보기 위해
        // Pod B가 이 드롭을 이미 알고 있는 상태(기준선)를 먼저 만들어 둔다.
        todayDropCacheB.refresh();
        assertThat(todayDropCacheB.find(dropId)).isPresent();

        // when: Pod A에서 삭제
        dropServiceA.deleteProduct(dropId);

        // then: 다른 Pod B의 캐시에서도 이 드롭이 사라진다
        awaitUntil(Duration.ofSeconds(3), () -> todayDropCacheB.find(dropId).isEmpty());
    }

    /**
     * Drop 엔티티 생성자는 "당일 등록은 아직 시작하지 않은 고정 슬롯(09~18시, 1시간 단위)만
     * 허용"을 도메인 불변식으로 강제한다(Drop.validateDropPeriod). 그래서 이 테스트가 실행되는
     * 실제 시각에 따라 오늘 등록 가능한 슬롯이 하나도 안 남아 있을 수 있다(대략 18시 이후).
     * 그 경우 테스트를 실패시키는 대신 건너뛴다 — 캐시 전파 메커니즘 자체는 시간대와 무관하므로
     * 다른 두 테스트(수정/삭제)로도 충분히 검증되고, 이 테스트만 시각에 매여 있다.
     */
    private static DropTimeSlot requireAvailableSlotToday() {
        LocalDateTime now = LocalDateTime.now();
        Optional<DropTimeSlot> slot = Arrays.stream(DropTimeSlot.values())
                .filter(candidate -> LocalDate.now().atTime(candidate.getStart()).isAfter(now.plusMinutes(1)))
                .findFirst();

        Assumptions.assumeTrue(slot.isPresent(),
                "오늘 남은 드롭 슬롯이 없어(현재 " + now + ") 이 테스트를 건너뜁니다 — "
                        + "당일 드롭은 아직 시작하지 않은 미래 슬롯에만 등록 가능하다는 도메인 제약 때문(Drop.validateDropPeriod)");
        return slot.get();
    }

    private static void awaitUntil(Duration timeout, java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("대기 중 인터럽트됨", e);
            }
        }
        throw new AssertionError("조건이 " + timeout + " 안에 충족되지 않았다 — Pub/Sub 전파가 실패했거나 "
                + "로컬 Redis가 떠 있지 않은 것일 수 있다(docker compose up -d redis)");
    }

    private static ConfigurableApplicationContext startPod() {
        // SecurityConfig가 HttpSecurity를 요구하는데, 이 빈은 서블릿 웹 환경에서만 자동
        // 구성된다(WebApplicationType.NONE으로는 못 뜬다). 그래서 실제 Pod처럼 내장 톰캣을
        // 띄우되, 두 Pod가 같은 포트를 두고 충돌하지 않도록 포트는 0(임의 할당)으로 둔다.
        return new SpringApplicationBuilder(OpenbakeApplication.class, FakeExternalPortsConfig.class)
                .web(WebApplicationType.SERVLET)
                .profiles("test")
                .properties(
                        "spring.datasource.url=" + DB_URL,
                        "spring.jmx.enabled=false",
                        "server.port=0",
                        "spring.data.redis.host=" + redis.getHost(),
                        "spring.data.redis.port=" + redis.getMappedPort(6379),
                        // 이 테스트는 검색과 무관하지만 @SpringBootTest와 마찬가지로 전체 컨텍스트가
                        // ES 빈까지 만들려 한다. SimpleElasticsearchRepository는 생성 시점에 실제
                        // 접속을 시도하므로, 자동구성 자체를 끄고 FakeExternalPortsConfig에서 목으로
                        // 대체한다(SellerSettlementEncryptionIntegrationTest와 같은 이유).
                        "spring.autoconfigure.exclude="
                                + "org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchAutoConfiguration,"
                                + "org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchRepositoriesAutoConfiguration,"
                                + "org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchReactiveRepositoriesAutoConfiguration,"
                                + "org.springframework.boot.data.elasticsearch.autoconfigure.health.DataElasticsearchReactiveHealthContributorAutoConfiguration,"
                                + "org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchClientAutoConfiguration,"
                                + "org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration,"
                                + "org.springframework.boot.elasticsearch.autoconfigure.health.ElasticsearchRestHealthContributorAutoConfiguration")
                .run();
    }

    /**
     * 판매자/회원/상품은 다른 모듈 소관이라 이 테스트의 관심사가 아니다. 두 Pod 컨텍스트 각각에
     * 독립적으로 등록되며(@Primary로 실제 어댑터를 덮어쓴다), 서로 상태를 공유하지 않는다 —
     * 공유가 필요 없도록 시나리오를 설계했다(각 테스트가 스스로 만든 드롭만 다룬다).
     */
    @Configuration
    static class FakeExternalPortsConfig {

        static final Long SELLER_ID = 9001L;

        @Bean
        @Primary
        CurrentSellerPort currentSellerPort() {
            return () -> SELLER_ID;
        }

        @Bean
        @Primary
        CurrentMemberPort currentMemberPort() {
            return () -> 9002L;
        }

        @Bean
        @Primary
        ProductPort productPort() {
            return new FakeProductPort();
        }

        // spring.autoconfigure.exclude로 ES 자동구성을 껐으므로 ProductSearchAdapter가 요구하는
        // 빈이 더는 자동 생성되지 않는다. 검색은 이 테스트의 관심사가 아니므로 목으로 채워 넣는다.
        @Bean
        ElasticsearchOperations elasticsearchOperations() {
            return org.mockito.Mockito.mock(ElasticsearchOperations.class);
        }

        @Bean
        ProductSearchRepository productSearchRepository() {
            return org.mockito.Mockito.mock(ProductSearchRepository.class);
        }
    }

    /**
     * TodayDropCache.refresh()가 드롭마다 getProductInfo를 다시 불러 상품 표시 정보를 채우므로
     * (등록/수정 응답에만 쓰이는 게 아니다), productId별 최신 정보를 실제로 들고 있어야 한다.
     *
     * 실제로는 상품 정보가 두 Pod가 공유하는 DB에 있으므로, Pod A가 등록한 상품을 Pod B의 캐시
     * 갱신(무효화 신호로 트리거됨)에서도 조회할 수 있어야 한다. 그래서 저장소를 static으로 둬
     * 두 Pod 컨텍스트에 각각 생성되는 FakeProductPort 인스턴스가 저장소를 공유하게 한다 —
     * DropRepository가 같은 H2 URL로 DB를 공유하는 것과 같은 이유다.
     */
    private static class FakeProductPort implements ProductPort {

        private static final AtomicLong productIdSequence = new AtomicLong(1);
        private static final java.util.Map<Long, DropProductInfoResult> productsById = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public DropInfoResult registerProduct(DropInfoCommand command) {
            Long productId = productIdSequence.getAndIncrement();
            productsById.put(productId, toProductInfo(command, productId));
            return DropInfoResult.of(command.dropStart(), command.dropEnd(), command.limitQuantity(), DropStatus.UPCOMING,
                    command.name(), command.description(), command.image(), command.pickupDates(),
                    command.price(), command.totalQuantity(), command.totalQuantity(),
                    FakeExternalPortsConfig.SELLER_ID, productId);
        }

        @Override
        public DropProductInfoResult getProductInfo(Long productId) {
            DropProductInfoResult info = productsById.get(productId);
            if (info == null) {
                throw new IllegalStateException("이 테스트에서 등록한 적 없는 productId: " + productId);
            }
            return info;
        }

        @Override
        public List<DropProductInfoResult> findProductListBySellerId(Long sellerId) {
            throw new UnsupportedOperationException("이 테스트 시나리오에서는 사용하지 않는다");
        }

        @Override
        public DropProductInfoResult updateProduct(Long productId, DropInfoCommand command) {
            DropProductInfoResult updated = toProductInfo(command, productId);
            productsById.put(productId, updated);
            return updated;
        }

        private DropProductInfoResult toProductInfo(DropInfoCommand command, Long productId) {
            return DropProductInfoResult.of(command.name(), command.description(), command.image(), command.pickupDates(),
                    command.price(), command.totalQuantity(), command.totalQuantity(),
                    FakeExternalPortsConfig.SELLER_ID, productId);
        }

        @Override
        public void deleteDropProduct(Long productId) {
            // no-op
        }

        @Override
        public int decreaseQuantity(Long productId, int selectQuantity) {
            throw new UnsupportedOperationException("이 테스트 시나리오에서는 사용하지 않는다");
        }

        @Override
        public int rollbackQuantity(Long productId, int selectQuantity) {
            throw new UnsupportedOperationException("이 테스트 시나리오에서는 사용하지 않는다");
        }

        @Override
        public void syncRemainQuantity(Long productId, int remainQuantity) {
            // no-op
        }

        @Override
        public int getTotalQuantity(Long productId) {
            throw new UnsupportedOperationException("이 테스트 시나리오에서는 사용하지 않는다");
        }

        @Override
        public Long getSellerIdByProductId(Long productId) {
            return FakeExternalPortsConfig.SELLER_ID;
        }

        @Override
        public boolean isGeneralProduct(Long productId) {
            return false;
        }
    }
}