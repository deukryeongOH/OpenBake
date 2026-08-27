package com.openbake.ai.domain;

import java.util.Optional;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProductEmbeddingTaskRepository {

    Optional<ProductEmbeddingTask> findLockedByProductId(Long productId);

    Optional<ProductEmbeddingTask> findById(Long id);

    Optional<ProductEmbeddingTask> findLockedById(Long id);

    Optional<ProductEmbeddingTask> claimNext();

    ProductEmbeddingTask save(ProductEmbeddingTask task);

    List<ProductEmbeddingTask> findRecoverable(Instant now);

    List<ProductEmbeddingTask> findAllById(Collection<Long> ids);

    long countByStatus(EmbeddingTaskStatus status);

    /**
     * lease가 만료됐는데 아직 {@code PROCESSING}으로 남아 있는 작업 수.
     *
     * <p><b>worker 중단을 직접 가리키는 신호다.</b> worker가 죽으면 자기가 잡고 있던
     * 작업을 놓지 못한다. 그런데 {@code PROCESSING} 건수는 그대로라 정상과 구별되지
     * 않는다. lease는 시간이 지나면 만료되므로 "만료됐는데 아직 PROCESSING"인 것이
     * 곧 "잡은 채로 사라진 작업"이다.
     *
     * <p>정상값은 0에 가깝다. 회수 배치가 곧 가져가기 때문이다. 계속 0이 아니면
     * 회수 배치까지 멈췄다는 뜻이다.
     */
    long countExpiredLease(Instant now);

    /**
     * 가장 오래 대기 중인 작업의 생성 시각.
     *
     * <p>건수만으로는 "많지만 빨리 도는 중"과 "적지만 영영 안 풀리는 중"을 구별할 수
     * 없다. 후자가 사고다. 결제 미결 지표에서 나이를 함께 두는 것과 같은 이유다.
     *
     * @return 대기 중인 작업이 없으면 {@link Optional#empty()}
     */
    Optional<Instant> findOldestPendingCreatedAt();
}
