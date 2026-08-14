package com.openbake.product.application.event;

import com.openbake.product.application.port.ProductSearchPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIndexEventListener {

    private final ProductSearchPort productSearchPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductIndexEvent event) {
        try {
            switch (event.eventType()) {
                case SAVED -> {
                    productSearchPort.index(event.product());
                    log.info("ES 인덱스 저장 완료. productId={}", event.productId());
                }
                case DELETED -> {
                    productSearchPort.deleteIndex(event.productId());
                    log.info("ES 인덱스 삭제 완료. productId={}", event.productId());
                }
            }
        } catch (Exception e) {
            log.error("ES 인덱스 동기화 실패. eventType={}, productId={}",
                    event.eventType(), event.productId(), e);
        }
    }
}
