package com.openbake.settlement.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.settlement.application.SettlementQueryService;
import com.openbake.settlement.application.SettlementResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/settlements")
@RequiredArgsConstructor
@Tag(
        name = "admin-settlement-controller",
        description = "관리자 정산 조회"
)
public class AdminSettlementController {

    private final SettlementQueryService settlementQueryService;

    @Operation(
            summary = "관리자용 정산 단건 상세 조회",
            description = """
                지급 시작 전 관리자가 정산 대상 판매자와 금액을 확인할 수 있도록,
                settlementId 하나로 정산서 자체(판매자, 기간, 금액, 상태)를 조회합니다.

                기존 `/internal/v1/settlements/{settlementId}/payouts`는
                지급 이력만 보여줄 뿐 정산서 본문을 보여주지 않아,
                관리자가 판매자/금액을 확인하지 못한 채 지급을 시작하는 문제를 보완합니다.
                """
    )
    @GetMapping("/{settlementId}")
    public ApiResponse<SettlementResponse> getSettlement(
            @PathVariable Long settlementId
    ) {
        SettlementResult result =
                settlementQueryService.getSettlement(settlementId);

        return ApiResponse.ok(
                SettlementResponse.from(result)
        );
    }
}
