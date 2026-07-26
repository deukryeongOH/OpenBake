package com.openbake.drop.application.queue;

import lombok.Getter;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


@Getter
public class DropQueue {
    // 특정 dropId 내에서의 대기열
    private final Queue<Long> waitingQueue = new ConcurrentLinkedQueue<>();
    // 대기열 중복 진입 방지 Set
    private final Set<Long> waitingUsers = ConcurrentHashMap.newKeySet();
    // 진입 허용(Active) 유저 Set
    private final Set<Long> activeUsers = ConcurrentHashMap.newKeySet();

}
