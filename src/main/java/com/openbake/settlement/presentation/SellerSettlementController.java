package com.openbake.settlement.presentation;

import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.common.response.ApiResponse;
import com.openbake.seller.application.CurrentSellerProvider;
import com.openbake.settlement.application.SellerSettlementDetailResult;
import com.openbake.settlement.application.SellerSettlementQueryService;
import com.openbake.settlement.application.SellerSettlementSummary;
import com.openbake.settlement.presentation.swagger.SettlementSwaggerExamples;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/sellers/me/settlements")
@RequiredArgsConstructor
@Tag(
        name = "seller-settlement-controller",
        description = "판매자 정산 조회"
)
public class SellerSettlementController {
    private final CurrentSellerProvider currentSellerProvider;
    private final SellerSettlementQueryService sellerSettlementQueryService;
    /**
     * settlementId
     * +
     * sellerId
     * 판매자 10이 판매자 20의 정산 ID를 임의로 조회하는 것을 막을 수 있음
     * */
    @Operation(
            summary = "시나리오 4. 내 정산 목록 조회",
            description = """
                로그인한 승인 판매자의 월별 정산 목록을 조회합니다.

                ### 시연 예상 결과
                - 총 판매금액: 50,000원
                - 수수료: 5,000원
                - 지급 예정 금액: 45,000원
                - 정산 대상: 2건
                - 상태: READY
                """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "판매자 정산 목록 조회 성공",
            content = @Content(
                    examples = @ExampleObject(
                            value = SettlementSwaggerExamples
                                    .SELLER_SETTLEMENT_LIST_RESPONSE
                    )
            )
    )
    @GetMapping
    public ApiResponse<SellerSettlementListResponse>
    getSettlements() {
        Long sellerId = getRequiredSellerId();
        List<SellerSettlementSummary> summaries =
                sellerSettlementQueryService
                        .getSettlements(sellerId);

        return ApiResponse.ok(
                SellerSettlementListResponse.from(summaries)
        );
    }

    @Operation(
            summary = "시나리오 4. 내 정산 상세 조회",
            description = """
                로그인 판매자의 정산서와 주문상품별 정산 상세를 조회합니다.

                ### 확인 사항
                - 상세 `lines`가 2건인지 확인
                - 주문 ID와 주문상품 ID 확인
                - 상품명과 판매금액 확인
                - 상세 금액 합계와 정산서 합계가 같은지 확인
                """
    )
    @GetMapping("/{settlementId}")
    public ApiResponse<SellerSettlementDetailResponse>
    getSettlement(
            @PathVariable Long settlementId
    ) {
        Long sellerId = getRequiredSellerId();
        SellerSettlementDetailResult result =
                sellerSettlementQueryService.getSettlement(
                        sellerId,
                        settlementId
                );

        return ApiResponse.ok(
                SellerSettlementDetailResponse.from(result)
        );
    }

    private Long getRequiredSellerId() {
        return currentSellerProvider.getSellerId()
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "승인된 판매자 정보를 찾을 수 없습니다."
                        )
                );
    }
}