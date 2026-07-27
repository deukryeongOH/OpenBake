package com.openbake.settlement.presentation;

import com.openbake.settlement.application.SettlementEventResult;
import com.openbake.settlement.application.SettlementEventService;
import com.openbake.settlement.presentation.swagger.SettlementSwaggerExamples;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정산 이벤트를 수신하는 내부 API 컨트롤러입니다.
 *
 * 현재는 주문 도메인에서 전달하는 구매확정 이벤트를 처리합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/settlement-events")
@Tag(
        name = "settlement-event-controller",
        description = "구매확정 정산 이벤트"
)
public class SettlementEventController {

    private final SettlementEventService settlementEventService;

    /**
     * 구매확정 이벤트를 수신하여 정산 대상을 생성합니다.
     *
     * 새로운 정산 대상이 생성되면 201 Created,
     * 이미 처리한 이벤트라면 200 OK를 반환합니다.
     */
    @Operation(
            summary = "시나리오 1. 구매확정 이벤트 수신",
            description = """
                구매확정된 주문상품을 정산 대상으로 등록합니다.

                ### 처리 결과
                - `settlement_inbox_events`에 이벤트 저장
                - `settlement_targets`에 정산 대상 생성
                - 최초 상태는 `PENDING`
                - 최초 생성 시 `settlement_id`는 `null`

                ### 중복 처리
                같은 `eventId` 또는 `orderItemId`를 다시 전송해도
                정산 대상은 중복 생성되지 않습니다.

                ### 다음 단계
                구매확정 이벤트 2건을 생성한 뒤
                `POST /internal/v1/settlement-batches/monthly`를 실행합니다.
                """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "구매확정 주문상품 정보",
            content = @Content(
                    examples = {
                            @ExampleObject(
                                    name = "1번 주문 - 제주 당근 케이크",
                                    summary = "30,000원 정산 대상",
                                    value = SettlementSwaggerExamples
                                            .PURCHASE_CONFIRMED_REQUEST_1
                            ),
                            @ExampleObject(
                                    name = "2번 주문 - 제주 감귤 타르트",
                                    summary = "20,000원 정산 대상",
                                    value = SettlementSwaggerExamples
                                            .PURCHASE_CONFIRMED_REQUEST_2
                            )
                    }
            )
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "정산 대상 신규 생성",
                    content = @Content(
                            examples = @ExampleObject(
                                    name = "신규 생성",
                                    value = SettlementSwaggerExamples
                                            .PURCHASE_CONFIRMED_SUCCESS_RESPONSE
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "이미 처리된 중복 이벤트",
                    content = @Content(
                            examples = @ExampleObject(
                                    name = "중복 이벤트",
                                    value = SettlementSwaggerExamples
                                            .PURCHASE_CONFIRMED_DUPLICATE_RESPONSE
                            )
                    )
            )
    })
    @PostMapping("/purchase-confirmed")
    public ResponseEntity<SettlementEventResponse> receivePurchaseConfirmed(
            @RequestBody PurchaseConfirmedRequest request
    ) {
        SettlementEventResult result =
                settlementEventService.receive(
                        request.toCommand()
                );

        SettlementEventResponse response =
                SettlementEventResponse.from(result);

        if (result.duplicate()) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}