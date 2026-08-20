package com.openbake.ai.infrastructure.jpa;

import com.openbake.ai.domain.MemberProductInteraction;
import com.openbake.common.event.InteractionType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberProductInteractionJpaRepository
        extends JpaRepository<MemberProductInteraction, Long> {

    boolean existsByMemberIdAndProductIdAndInteractionTypeAndOccurredAtBetween(
            Long memberId, Long productId, InteractionType interactionType,
            Instant from, Instant through);

    List<MemberProductInteraction> findAllByMemberIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
            Long memberId, Instant cutoff);

    @Query("""
            SELECT interaction FROM MemberProductInteraction interaction
            WHERE interaction.dropId IS NULL
              AND ((interaction.interactionType = com.openbake.common.event.InteractionType.PURCHASE
                    AND interaction.occurredAt >= :purchaseCutoff)
                OR (interaction.interactionType = com.openbake.common.event.InteractionType.CART_ADD
                    AND interaction.occurredAt >= :cartCutoff))
            """)
    List<MemberProductInteraction> findPopularitySignals(
            @Param("purchaseCutoff") Instant purchaseCutoff,
            @Param("cartCutoff") Instant cartCutoff);

    @Modifying
    @Query("DELETE FROM MemberProductInteraction interaction WHERE interaction.memberId = :memberId")
    int hardDeleteByMemberId(@Param("memberId") Long memberId);

    @Modifying
    @Query(value = """
            DELETE FROM member_product_interactions
            WHERE id IN (
                SELECT id FROM member_product_interactions
                WHERE occurred_at < :cutoff
                ORDER BY occurred_at
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteBatchBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
