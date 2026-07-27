package com.openbake.settlement.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.settlement.application
        .MonthlySettlementBatchExecutionResult;
import com.openbake.settlement.application
        .MonthlySettlementBatchLauncher;
import com.openbake.settlement.application
        .MonthlySettlementBatchQueryService;
import com.openbake.settlement.application.MonthlySettlementBatchListResult;
import com.openbake.settlement.presentation.swagger.SettlementSwaggerExamples;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/internal/v1/settlement-batches")
@RequiredArgsConstructor
@Tag(
        name = "monthly-settlement-batch-controller",
        description = "월 정산 배치"
)
public class MonthlySettlementBatchController {

    private final MonthlySettlementBatchLauncher
            monthlySettlementBatchLauncher;

    private final MonthlySettlementBatchQueryService
            monthlySettlementBatchQueryService;

    @Operation(
            summary = "시나리오 2. 월 정산 배치 실행",
            description = """
                지정한 기간의 PENDING 정산 대상을 판매자별로 집계합니다.

                ### 생성 데이터
                - `settlements`: 판매자별 월 정산서
                - `settlement_lines`: 주문상품별 정산 상세

                ### 대상 상태 변경
                - `settlement_targets.status`: `PENDING → ASSIGNED`
                - `settlement_targets.settlement_id`: 생성된 정산서 ID 연결

                ### 시연 예상값
                | 항목 | 예상값 |
                |---|---:|
                | 총 판매금액 | 50,000원 |
                | 수수료 | 5,000원 |
                | 지급 예정 금액 | 45,000원 |
                | 정산 대상 | 2건 |
                """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    examples = @ExampleObject(
                            name = "2026년 7월 정산",
                            summary = "7월 구매확정 건 집계",
                            value = SettlementSwaggerExamples
                                    .MONTHLY_BATCH_REQUEST
                    )
            )
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "월 정산 Job 실행 요청 성공",
            content = @Content(
                    examples = @ExampleObject(
                            value = SettlementSwaggerExamples
                                    .MONTHLY_BATCH_RESPONSE
                    )
            )
    )
    @PostMapping("/monthly")
    public ApiResponse<MonthlySettlementBatchResponse>
    runMonthlySettlement(
            @Valid
            @RequestBody MonthlySettlementBatchRequest request
    ) {
        JobExecution jobExecution =
                monthlySettlementBatchLauncher.launch(
                        request.periodStart(),
                        request.periodEnd()
                );

        MonthlySettlementBatchResponse response =
                new MonthlySettlementBatchResponse(
                        jobExecution.getId(),
                        jobExecution
                                .getJobInstance()
                                .getJobName(),
                        jobExecution
                                .getStatus()
                                .name()
                );

        return ApiResponse.ok(response);
    }

    @Operation(
            summary = "시나리오 3. 월 정산 배치 실행 상세 조회",
            description = """
                월 정산 실행 API에서 받은 `jobExecutionId`로
                배치의 실행 상태를 조회합니다.

                ### 확인 사항
                - `status`가 `COMPLETED`인지 확인
                - `periodStart`, `periodEnd`가 요청값과 같은지 확인
                - 시작 및 종료 시각 확인

                `COMPLETED`는 Job이 오류 없이 종료됐다는 의미이며,
                실제 생성 건수는 `settlementCount`, `targetCount`로 확인합니다.
                """
    )
    @GetMapping("/{jobExecutionId}")
    public ApiResponse<MonthlySettlementBatchExecutionResponse>
    getBatchExecution(
            @PathVariable Long jobExecutionId
    ) {
        MonthlySettlementBatchExecutionResult result =
                monthlySettlementBatchQueryService
                        .getExecution(jobExecutionId);

        return ApiResponse.ok(
                MonthlySettlementBatchExecutionResponse.from(
                        result
                )
        );
    }

    /** 위치 주의 */
    @Operation(
            summary = "시나리오 3. 월 정산 배치 실행 목록 조회",
            description = """
                최근 월 정산 배치 실행 이력을 조회합니다.

                ### 확인 사항
                - 최근 실행 순서
                - 실행 상태
                - 정산 대상 기간
                - 시작·종료 시각
                """
    )
    @GetMapping
    public ApiResponse<MonthlySettlementBatchListResponse>
    getBatchExecutions(
            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {
        MonthlySettlementBatchListResult result =
                monthlySettlementBatchQueryService.getExecutions(
                        page,
                        size
                );

        return ApiResponse.ok(
                MonthlySettlementBatchListResponse.from(result)
        );
    }
}