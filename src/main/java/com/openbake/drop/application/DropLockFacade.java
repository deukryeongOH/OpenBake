package com.openbake.drop.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
@Slf4j
public class DropLockFacade {

    private final DropLockService dropLockService;
    private final ConcurrentHashMap<Long, ReentrantLock> lockConcurrentHashMap = new ConcurrentHashMap<>();

    public void reserveStock(Long dropId, Long memberId, int quantity) {
        // 1인당 제한 수량과 선택 수량 검증
        dropLockService.checkLimitQuantityPerPerson(dropId, quantity);
        // 대기열에 통과된 직후인지 상태 검증
        dropLockService.checkEntryStatus(dropId, memberId);

        ReentrantLock lock = lockConcurrentHashMap.computeIfAbsent(dropId, d -> new ReentrantLock());

        long waitStart = System.currentTimeMillis();
        boolean getLock = false;
        try{
            // 3초 동안 락 획득 시도 ( 동시 요청 폭주 시 쓰레드 락 대기 시간 제한함.)
            getLock = lock.tryLock(3, TimeUnit.SECONDS);
            log.info("lock wait: {}ms", System.currentTimeMillis() - waitStart);

            if (!getLock) {
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            // 락을 획득했으니 재고 차감
            dropLockService.decreaseQuantity(dropId, memberId, quantity);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
        } finally {
            // 현재 쓰레드가 락을 잡고 있는 경우에만 unlock 해제
            if (getLock && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        dropLockService.confirmEventPublisher(dropId, memberId, quantity);
    }
}
