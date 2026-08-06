package com.openbake.drop.application.queue;


import java.util.Set;

// 추후 redis로의 확장성 고려해 인터페이스 사용
public interface QueueManager {
    Long enqueue(Long dropId, Long memberId);
    Long getRank(Long dropId, Long memberId);
    void allowEntries(Long dropId, int count);
    void removeActiveUser(Long dropId, Long memberId);
    boolean isActive(Long dropId, Long memberId);
    void finishDrop(Long dropId);
    Set<Long> checkActiveMembers(Long dropId);
    void markSoldOut(Long dropId);
    void unmarkSoldOut(Long dropId);
}
