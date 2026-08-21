package com.openbake.ai.application;

import com.openbake.ai.domain.ProductEmbeddingTask;
import com.openbake.ai.domain.ProductEmbeddingTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmbeddingTaskOperationsService {

    private final ProductEmbeddingTaskRepository taskRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<FailedTask> failedTasks() {
        return taskRepository.findRecoverable(clock.instant()).stream()
                .map(FailedTask::from)
                .toList();
    }

    @Transactional
    public RetryResult retry(List<Long> requestedTaskIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>(requestedTaskIds == null
                ? List.of() : requestedTaskIds);
        List<Long> retried = new ArrayList<>();
        List<Long> skipped = new ArrayList<>();
        Instant now = clock.instant();
        for (Long taskId : ids) {
            if (taskId == null) {
                continue;
            }
            taskRepository.findLockedById(taskId)
                    .filter(task -> task.retryIfRecoverable(now))
                    .ifPresentOrElse(task -> retried.add(taskId), () -> skipped.add(taskId));
        }
        return new RetryResult(List.copyOf(retried), List.copyOf(skipped));
    }

    public record FailedTask(
            Long taskId,
            Long productId,
            String status,
            int retryCount,
            String lastErrorCode,
            Instant updatedAt) {

        static FailedTask from(ProductEmbeddingTask task) {
            return new FailedTask(
                    task.getId(), task.getProductId(), task.getStatus().name(),
                    task.getRetryCount(), task.getLastErrorCode(), task.getUpdatedAt());
        }
    }

    public record RetryResult(List<Long> retriedTaskIds, List<Long> skippedTaskIds) {
    }
}
