package com.openbake.product.infrastructure.elasticsearch;

import com.openbake.product.application.port.ProductSearchPort;
import com.openbake.product.domain.Product;
import com.openbake.product.domain.ProductRepository;
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
        log.info("[재색인] 배치 시작");

        // 1. RDB에서 GENERAL 상품 전체 조회 (ES 색인 대상)
        List<Product> products = productRepository.findAllByType(Type.GENERAL);

        // 2. ES에 bulk upsert
        if (!products.isEmpty()) {
            productSearchPort.indexAll(products);
        }

        // 3. 고아 문서 정리 — RDB에 없는데 ES에만 남은 문서 삭제
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
    }
}
