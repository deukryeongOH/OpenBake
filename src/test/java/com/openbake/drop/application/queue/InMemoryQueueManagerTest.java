package com.openbake.drop.application.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryQueueManagerTest {

    private final InMemoryQueueManager queueManager = new InMemoryQueueManager();

    @Test
    @DisplayName("해당 dropId의 대기열이 아직 만들어진 적 없으면 null이 아닌 빈 Set을 반환한다")
    void checkActiveMembers_NoQueueCreatedYet_ReturnsEmptySetNotNull() {
        // when
        Set<Long> result = queueManager.checkActiveMembers(999L);

        // then
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("대기열이 존재하면 만료 체크를 실제 DropQueue에 위임한다")
    void checkActiveMembers_WithQueue_DelegatesToDropQueue() {
        // given
        Long dropId = 1L;
        Long memberId = 10L;
        queueManager.enqueue(dropId, memberId);
        queueManager.allowEntries(dropId, 10); // waiting -> active로 승격

        // when & then (방금 승격돼 아직 10분이 안 지났으므로 만료자는 없어야 한다)
        assertThat(queueManager.checkActiveMembers(dropId)).isEmpty();
        assertThat(queueManager.isActive(dropId, memberId)).isTrue();
    }
}