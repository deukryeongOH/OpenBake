package com.openbake.settlement.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.settlement.application.SettlementPayoutQueryService;
import com.openbake.settlement.application.SettlementPayoutResult;
import com.openbake.settlement.application.SettlementPayoutService;
import com.openbake.settlement.presentation.swagger.SettlementSwaggerExamples;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
@Tag(
        name = "admin-settlement-payout-controller",
        description = "관리자 정산 지급"
)
public class AdminSettlementPayoutController {

    private final SettlementPayoutService payoutService;
    private final SettlementPayoutQueryService payoutQueryService;

    @Operation(
            summary = "시나리오 5. 정산 지급 시작",
            description = """
                READY 상태의 정산에 대한 지급 원장을 생성합니다.

                ### 상태 변경
                - Settlement: `READY → PAYING`
                - SettlementPayout: `REQUESTED → PROCESSING`

                ### 계좌 처리
                - 정산서에 저장된 sellerId로 판매자 계좌 조회
                - 지급 당시 계좌정보를 지급 원장에 암호화 스냅샷으로 저장

                ### 멱등성
                같은 `settlementId`와 `idempotencyKey`로 재요청하면
                신규 원장을 만들지 않고 기존 payoutId를 반환합니다.
                """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    examples = @ExampleObject(
                            name = "정산 1 지급 시작",
                            value = SettlementSwaggerExamples
                                    .PAYOUT_START_REQUEST
                    )
            )
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "지급 시작 또는 기존 멱등 결과 반환",
            content = @Content(
                    examples = @ExampleObject(
                            value = SettlementSwaggerExamples
                                    .PAYOUT_START_RESPONSE
                    )
            )
    )
    @PostMapping("/settlements/{settlementId}/payouts")
    public ApiResponse<SettlementPayoutResponse> start(
            @PathVariable Long settlementId,
            @Valid
            @RequestBody SettlementPayoutStartRequest request
    ) {
        SettlementPayoutResult result =
                payoutService.start(
                        settlementId,
                        request.idempotencyKey()
                );

        return ApiResponse.ok(
                SettlementPayoutResponse.from(result)
        );
    }

    @Operation(
            summary = "시나리오 6-A. 지급 성공 처리",
            description = """
                외부 은행 송금이 성공한 지급 원장을 완료 처리합니다.

                ### 상태 변경
                - SettlementPayout: `PROCESSING → COMPLETED`
                - Settlement: `PAYING → COMPLETED`

                ### 저장 항목
                - 외부 거래 ID
                - 완료 시각

                성공과 실패 API는 같은 payoutId에 동시에 실행하지 않습니다.
                """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    examples = @ExampleObject(
                            value = SettlementSwaggerExamples
                                    .PAYOUT_COMPLETE_REQUEST
                    )
            )
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "지급 완료",
            content = @Content(
                    examples = @ExampleObject(
                            value = SettlementSwaggerExamples
                                    .PAYOUT_COMPLETE_RESPONSE
                    )
            )
    )
    @PostMapping("/settlement-payouts/{payoutId}/complete")
    public ApiResponse<SettlementPayoutResponse> complete(
            @PathVariable Long payoutId,
            @Valid
            @RequestBody SettlementPayoutCompleteRequest request
    ) {
        SettlementPayoutResult result =
                payoutService.complete(
                        payoutId,
                        request.externalTransactionId()
                );

        return ApiResponse.ok(
                SettlementPayoutResponse.from(result)
        );
    }

    @Operation(
            summary = "시나리오 6-B. 지급 실패 처리",
            description = """
                외부 은행 송금에 실패한 지급 원장을 실패 처리합니다.

                ### 상태 변경
                - SettlementPayout: `PROCESSING → FAILED`
                - Settlement: `PAYING → FAILED`

                ### 저장 항목
                - 실패 사유
                - 실패 시각

                실패 시 `externalTransactionId`와 `completedAt`은 저장되지 않습니다.
                """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    examples = @ExampleObject(
                            value = SettlementSwaggerExamples
                                    .PAYOUT_FAIL_REQUEST
                    )
            )
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "지급 실패 처리",
            content = @Content(
                    examples = @ExampleObject(
                            value = SettlementSwaggerExamples
                                    .PAYOUT_FAIL_RESPONSE
                    )
            )
    )
    @PostMapping("/settlement-payouts/{payoutId}/fail")
    public ApiResponse<SettlementPayoutResponse> fail(
            @PathVariable Long payoutId,
            @Valid
            @RequestBody SettlementPayoutFailRequest request
    ) {
        SettlementPayoutResult result =
                payoutService.fail(
                        payoutId,
                        request.failureReason()
                );

        return ApiResponse.ok(
                SettlementPayoutResponse.from(result)
        );
    }

    @GetMapping("/settlement-payouts/{payoutId}")
    public ApiResponse<SettlementPayoutResponse> getPayout(
            @PathVariable Long payoutId
    ) {
        SettlementPayoutResult result =
                payoutQueryService.getPayout(payoutId);

        return ApiResponse.ok(
                SettlementPayoutResponse.from(result)
        );
    }

    @GetMapping("/settlements/{settlementId}/payouts")
    public ApiResponse<SettlementPayoutListResponse> getPayouts(
            @PathVariable Long settlementId
    ) {
        List<SettlementPayoutResult> results =
                payoutQueryService.getPayouts(settlementId);

        return ApiResponse.ok(
                SettlementPayoutListResponse.from(results)
        );
    }
}