package com.openbake.drop.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DropLockFacadeTest {

    @Mock
    private DropLockService dropLockService;

    @Test
    @DisplayName("같은 dropId에 대한 동시 요청은 락으로 직렬화되어 순차 처리된다")
    void reserveStock_ConcurrentRequests_SerializedByLock() throws InterruptedException {
        // given
        DropLockFacade dropLockFacade = new DropLockFacade(dropLockService);

        Long dropId = 1L;
        int threadCount = 10;

        AtomicInteger currentConcurrent = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        doAnswer(invocation -> {
            int concurrent = currentConcurrent.incrementAndGet();
            maxConcurrent.updateAndGet(max -> Math.max(max, concurrent));
            Thread.sleep(50); // 락을 점유하고 있는 동안 다른 스레드가 들어오는지 확인하기 위한 지연
            currentConcurrent.decrementAndGet();
            return null;
        }).when(dropLockService).decreaseQuantity(anyLong(), anyLong(), anyInt());

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // when
        try (ExecutorService executorService = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                long memberId = i + 1L;
                executorService.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        dropLockFacade.reserveStock(dropId, memberId, 1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown(); // 모든 스레드 동시 출발

            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            assertThat(completed).as("모든 스레드가 제한 시간 내에 끝나지 않음").isTrue();
        }

        // then
        assertThat(maxConcurrent.get()).isEqualTo(1); // 동시에 락 안에 있던 스레드는 항상 1개 (직렬화 확인)
        verify(dropLockService, times(threadCount)).decreaseQuantity(eq(dropId), anyLong(), eq(1));
        verify(dropLockService, times(threadCount)).confirmEventPublisher(eq(dropId), anyLong(), eq(1));
    }
}