package com.openbake.drop.application.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DropQueueTest {

    private final DropQueue dropQueue = new DropQueue();

    @Test
    @DisplayName("만료 시각이 지난 active 멤버는 만료 목록으로 반환되고 activeMembers에서도 제거된다")
    void checkMemberIsExpired_RemovesAndReturnsExpiredMembers() {
        // given
        Long expiredMemberId = 1L;
        Long stillActiveMemberId = 2L;
        dropQueue.addActiveMember(expiredMemberId, LocalDateTime.now().minusSeconds(1));
        dropQueue.addActiveMember(stillActiveMemberId, LocalDateTime.now().plusMinutes(10));

        // when
        Set<Long> expired = dropQueue.checkMemberIsExpired();

        // then
        assertThat(expired).containsExactly(expiredMemberId);
        assertThat(dropQueue.checkMemberIsActive(expiredMemberId)).isFalse();
        assertThat(dropQueue.checkMemberIsActive(stillActiveMemberId)).isTrue();
    }

    @Test
    @DisplayName("만료된 active 멤버가 없으면 빈 Set을 반환한다")
    void checkMemberIsExpired_NoExpiredMembers_ReturnsEmptySet() {
        // given
        dropQueue.addActiveMember(1L, LocalDateTime.now().plusMinutes(10));

        // when & then
        assertThat(dropQueue.checkMemberIsExpired()).isEmpty();
    }

    @Test
    @DisplayName("active 멤버가 아예 없으면 빈 Set을 반환한다")
    void checkMemberIsExpired_NoActiveMembersAtAll_ReturnsEmptySet() {
        assertThat(dropQueue.checkMemberIsExpired()).isEmpty();
    }
}