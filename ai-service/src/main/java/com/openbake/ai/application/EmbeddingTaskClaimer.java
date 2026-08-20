package com.openbake.ai.application;

import com.openbake.ai.application.EmbeddingTaskSnapshot.TaskSnapshot;
import com.openbake.ai.domain.ProductEmbeddingTaskRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmbeddingTaskClaimer {

    private final ProductEmbeddingTaskRepository taskRepository;
    private final EmbeddingProperties properties;

    @Transactional
    public Optional<TaskSnapshot> claimNext() {
        Instant now = Instant.now();
        return taskRepository.claimNext().map(task -> {
            task.claim(now, properties.worker().lease());
            return EmbeddingTaskSnapshot.from(task);
        });
    }
}
