package com.openbake.product.infrastructure.elasticsearch;

import com.openbake.product.application.port.ProductSearchPort;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
import com.openbake.product.domain.ProductStatus;
import com.openbake.product.domain.Type;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
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

    private final ProductRepository productRepository;
    private final ProductSearchPort productSearchPort;

    @Scheduled(cron = "${openbake.reindex.cron}")
    public void reindex() {
        log.info("[재색인] 스케줄 배치 시작");
        reindexNow();
    }

    /**
     * cutover 등 원하는 시점에 한 번 실행해야 할 때 쓰는 진입점.
     * {@link com.openbake.product.presentation.AdminProductReindexController}가 호출한다.
     */
    public ReindexResult reindexNow() {
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
