package com.openbake.drop.application.queue;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;


@Getter
public class DropQueue {
    // 특정 dropId 내에서의 대기열
    private final Queue<Long> waitingQueue = new ConcurrentLinkedQueue<>();
    // 대기열 중복 진입 방지 Set (존재 여부)
    private final Set<Long> waitingMembers = ConcurrentHashMap.newKeySet();
    // 진입 허용(Active) 유저 Set
    private final Map<Long, LocalDateTime> activeMembers = new ConcurrentHashMap<>();
    // member가 가진 티켓 번호 (순번 계산)
    private final Map<Long, Long> memberByTicketNumber = new ConcurrentHashMap<>();
    // 티켓 번호
    private final AtomicLong ticketNumber = new AtomicLong(0);
    // 입장 번호
    private final AtomicLong entryCount = new AtomicLong(0);

    public long issueTicket(){
        return ticketNumber.incrementAndGet();
    }

    public void addQueueingMember(Long memberId) {
        waitingQueue.add(memberId);
    }

    public boolean checkMemberIsActive(Long memberId) {
        LocalDateTime expiresAt = activeMembers.get(memberId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(LocalDateTime.now())) {
            activeMembers.remove(memberId);
            return false;
        }
        return true;
    }

    public void checkMemberIsExpired(){
        for (Long memberId : activeMembers.keySet()) {
            if (activeMembers.get(memberId).isBefore(LocalDateTime.now())) {
                activeMembers.remove(memberId);
            }
        }
    }

    public boolean checkMemberIsWaiting(Long memberId) {
        return waitingMembers.contains(memberId);
    }

    public Long returnNowRank(Long memberId){
        return memberByTicketNumber.get(memberId) - entryCount.get();
    }

    public boolean addWaitingMember(Long memberId) {
        return waitingMembers.add(memberId);
    }

    public void issueTicketForMember(Long memberId){
        memberByTicketNumber.put(memberId, issueTicket());
    }

    public Long pollingNext(){
        return getWaitingQueue().poll();
    }

    public void removeWaitingMember(Long memberId){
        waitingMembers.remove(memberId);
    }

    public void addActiveMember(Long memberId, LocalDateTime localDateTime) {
        activeMembers.put(memberId, localDateTime);
    }

    public void increaseEntryCount(){
        entryCount.incrementAndGet();
    }

    public void removeTicket(Long memberId){
        memberByTicketNumber.remove(memberId);
    }

    public void removeActiveMember(Long memberId) {
        activeMembers.remove(memberId);
    }
}
