package com.openbake.product.infrastructure.elasticsearch;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.product.application.port.ProductSearchPort;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.domain.ProductStatus;
import com.openbake.product.domain.Type;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RDB ↔ ES 데이터 드리프트를 보정하는 재색인 배치.
 *
 * 실시간 이벤트(@TransactionalEventListener)가 유실될 수 있는 상황에 대한 안전망이다.
 * - ES 일시 장애 중 상품 등록/수정
 * - 이벤트 발행 후 네트워크 에러
 * - 서버가 이벤트 처리 전에 종료
 *
 * 주기는 application.yml의 openbake.reindex.cron으로 조정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductReindexScheduler {

    private static final String LOCK_NAME = "productReindex";
    // 실제 소요 시간을 재본 기록이 없어 넉넉하게 잡았다. 하루 1회 배치라 여유를 둬도 무방하다.
    // 로그(재색인 배치 완료)로 실제 소요 시간이 확인되면 좁힌다. docs/14 참고.
    //
    // ⚠️ 아래 값은 reindex()의 @SchedulerLock(lockAtMostFor/lockAtLeastFor) 문자열과 반드시
    // 같아야 한다. 애노테이션 속성은 컴파일 타임 상수만 받을 수 있어 이 Duration 상수를 그대로
    // 재사용할 수 없다 — 한쪽을 바꾸면 반드시 다른 쪽도 같이 바꿀 것.
    private static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(30);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ofMinutes(1);

    private final ProductRepository productRepository;
    private final ProductSearchPort productSearchPort;
    private final LockProvider lockProvider;

    /**
     * 인스턴스가 여러 대면 같은 시각에 두 대가 동시에 이 배치를 돌릴 수 있다. 그러면 한쪽이
     * 아직 색인하지 않은 신규 상품을 다른 쪽이 고아로 오판해 삭제할 수 있다(RDB 스냅샷과 ES
     * 조회 사이에 트랜잭션 경계가 없어서다). @SchedulerLock으로 정확히 1대만 실행되게 막는다.
     *
     * 여기서 락을 못 잡으면 조용히 건너뛴다 — 스케줄 배치라 결과를 기다리는 사람이 없다.
     * 관리자 수동 트리거({@link #reindexNowIfAvailable()})는 이 애노테이션을 타지 않으므로
     * 별도로 막는다.
     */
    @Scheduled(cron = "${openbake.reindex.cron}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void reindex() {
        log.info("[재색인] 스케줄 배치 시작");
        reindexNow();
    }

    /**
     * 관리자 수동 트리거 전용 진입점.
     * {@link com.openbake.product.presentation.AdminProductReindexController}가 호출한다.
     *
     * {@code reindex()}의 {@code @SchedulerLock}은 스케줄러가 호출할 때만 적용되고, 이 메서드를
     * 직접 부르는 경로는 그 애노테이션을 타지 않는다. 그래서 같은 이름의 락을 여기서 직접
     * 확인한다. 스케줄 배치와 달리 사람이 응답을 기다리므로, 조용히 건너뛰는 대신 명확한
     * 예외로 알린다.
     */
    public ReindexResult reindexNowIfAvailable() {
        LockConfiguration lockConfiguration = new LockConfiguration(
                Instant.now(), LOCK_NAME, LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
        Optional<SimpleLock> lock = lockProvider.lock(lockConfiguration);

        if (lock.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_REINDEX_ALREADY_RUNNING);
        }

        try {
            return reindexNow();
        } finally {
            lock.get().unlock();
        }
    }

    /**
     * 재색인 실제 실행부. 직접 호출하지 말고 위 두 진입점(스케줄 배치·관리자 수동 트리거)을
     * 거쳐야 한다 — 둘 다 락으로 보호되지만 이 메서드 자체는 그렇지 않다.
     */
    private ReindexResult reindexNow() {
        // 1. RDB에서 삭제되지 않은 GENERAL 상품 조회 (ES 색인 대상)
        //    품절 상품은 색인에 남긴다 — 검색 쿼리가 status 로 거르므로 결과는 같고,
        //    재입고 때마다 문서를 지웠다 다시 만드는 왕복을 피한다.
        List<Product> products = productRepository.findAllByType(Type.GENERAL).stream()
                .filter(product -> product.getStatus() != ProductStatus.DELETED)
                .toList();

        // 2. ES에 bulk upsert
        if (!products.isEmpty()) {
            productSearchPort.indexAll(products);
        }

        // 3. 고아 문서 정리 — RDB 색인 대상에 없는데 ES에만 남은 문서 삭제
        Set<Long> rdbIds = products.stream()
                .map(Product::getId)
                .collect(Collectors.toSet());

        List<Long> esIds = productSearchPort.findAllIndexedIds();
        List<Long> orphanIds = esIds.stream()
                .filter(id -> !rdbIds.contains(id))
                .toList();

        for (Long orphanId : orphanIds) {
            productSearchPort.deleteIndex(orphanId);
        }

        log.info("[재색인] 배치 완료 — upsert={}, orphan_deleted={}", products.size(), orphanIds.size());
        return new ReindexResult(products.size(), orphanIds.size());
    }

    public record ReindexResult(int upsertCount, int orphanDeletedCount) {
    }
}
