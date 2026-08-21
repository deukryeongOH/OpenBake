package com.openbake.ai.presentation;

import com.openbake.ai.application.EmbeddingBackfillService;
import com.openbake.ai.application.EmbeddingReconciliationService;
import com.openbake.ai.application.EmbeddingTaskOperationsService;
import com.openbake.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/ai/embeddings")
@RequiredArgsConstructor
public class AiOperationsController {

    private final EmbeddingBackfillService backfillService;
    private final EmbeddingReconciliationService reconciliationService;
    private final EmbeddingTaskOperationsService taskOperationsService;

    @PostMapping("/backfill")
    public ApiResponse<EmbeddingBackfillService.BackfillResult> backfill() {
        return ApiResponse.ok(backfillService.backfill());
    }

    @PostMapping("/reconcile")
    public ApiResponse<EmbeddingReconciliationService.ReconciliationResult> reconcile() {
        return ApiResponse.ok(reconciliationService.reconcile());
    }

    @GetMapping("/failed")
    public ApiResponse<List<EmbeddingTaskOperationsService.FailedTask>> failed() {
        return ApiResponse.ok(taskOperationsService.failedTasks());
    }

    @PostMapping("/retry")
    public ApiResponse<EmbeddingTaskOperationsService.RetryResult> retry(
            @Valid @RequestBody RetryRequest request) {
        return ApiResponse.ok(taskOperationsService.retry(request.taskIds()));
    }

    public record RetryRequest(@NotNull List<Long> taskIds) {
    }
}
