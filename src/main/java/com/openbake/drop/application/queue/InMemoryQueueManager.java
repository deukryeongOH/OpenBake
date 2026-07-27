package com.openbake.drop.application.queue;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryQueueManager implements QueueManager{

    // dropId별 DropQueue 객체 관리
    private final Map<Long, DropQueue> dropQueueMap = new ConcurrentHashMap<>();

    // dropId를 가진 대기열 가져오거나 없으면 생성
    private DropQueue getDropQueue(Long dropId) {
        return dropQueueMap.computeIfAbsent(dropId, d -> new DropQueue());
    }

    // 대기열 진입
    public Long enqueue(Long dropId, Long memberId) {
        DropQueue queue = getDropQueue(dropId);

        // 메소드 전체에 안하는 이유 (dropId = 1이 완료될 때까지 dropId = 2는 진입 대기)
        synchronized (queue) {  // DropQueue 객체에만 락을 검
            if (queue.checkMemberIsActive(memberId)) {
                return 0L; // 이미 드롭 입장함.
            }
            if (queue.addWaitingMember(memberId)) {
                queue.addQueueingMember(memberId);
                queue.issueTicketForMember(memberId);
            }

            return getRank(dropId, memberId);
        }
    }

    // 현재 내 대기 번호 조회 (프론트 폴링용)
    public Long getRank(Long dropId, Long memberId) {
        DropQueue queue = dropQueueMap.get(dropId);
        if (queue == null) {
            return -1L; // 대기열 자체가 존재하지 않음.
        }
        if (queue.checkMemberIsActive(memberId)) {
            return 0L; // 0순위 (입장 허용 상태)
        }

        if (!queue.checkMemberIsWaiting(memberId)) {
            return -1L; // 대기열에 없음
        }

        return queue.returnNowRank(memberId);
    }

    // 대기열 상위 n명을 active set으로 이동 (이건 schedular가 호출)
    public void allowEntries(Long dropId, int cnt) {
        DropQueue queue = dropQueueMap.get(dropId);
        if (queue == null) {
            return;
        }

        synchronized (queue) {
            for(int i = 0; i < cnt; i++){
                Long memberId = queue.pollingNext();

                if (memberId == null) {
                    break; // 대기열 빔.
                }

                queue.removeWaitingMember(memberId); // 대기열에서 없애고
                queue.addActiveMember(memberId, LocalDateTime.now().plusMinutes(10)); // active로 옮김 (10분 후 만료)
                queue.increaseEntryCount();
                queue.removeTicket(memberId);
            }

        }

    }

    // 드롭 완료 및 퇴장 시 권한 제거
    public void removeActiveUser(Long dropId, Long memberId) {
        DropQueue queue = dropQueueMap.get(dropId);
        if (queue != null) {
            queue.removeActiveMember(memberId);
        }
    }

    // 현재 대기열에 있거나 이미 진입 허용된 유저인지 확인
    public boolean isActive(Long dropId, Long memberId) {
        DropQueue queue = dropQueueMap.get(dropId);
        if (queue == null) {
            return false;
        }

        return queue.checkMemberIsActive(memberId);
    }

    // DropQueue에서 drop 제거
    public void finishDrop(Long dropId){
        dropQueueMap.remove(dropId);
    }

    public void checkActiveMembers(Long dropId){
        DropQueue dropQueue = dropQueueMap.get(dropId);
        if (dropQueue == null) {
            throw new IllegalArgumentException("대기열 자체가 없습니다.");
        }
        dropQueue.checkMemberIsExpired();
    }
}
