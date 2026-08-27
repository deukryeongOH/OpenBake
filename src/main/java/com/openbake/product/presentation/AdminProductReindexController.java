package com.openbake.product.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.product.infrastructure.elasticsearch.ProductReindexScheduler;
import com.openbake.product.presentation.dto.ProductReindexResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * cutover 등에서 임의 시점에 전체 재색인을 한 번 트리거하는 관리자 전용 endpoint.
 * {@code /internal/**}이므로 외부 Ingress에 노출되지 않는다({@code k8s/openbake/entrypoint/ingress.yaml} 참고).
 *
 * 스케줄 배치와 같은 락을 공유한다. 새벽 자동 배치와 겹치면 409(PR007)로 거부된다 —
 * 재색인이 동시에 두 번 도는 것을 막기 위함이다(docs/14 참고).
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/products/reindex")
@RequiredArgsConstructor
public class AdminProductReindexController {

    private final ProductReindexScheduler productReindexScheduler;

    @PostMapping
    public ApiResponse<ProductReindexResultResponse> reindexNow() {
        log.info("[재색인] 수동 트리거 요청 수신");
        ProductReindexScheduler.ReindexResult result = productReindexScheduler.reindexNowIfAvailable();
        return ApiResponse.ok(ProductReindexResultResponse.from(result));
    }
}
