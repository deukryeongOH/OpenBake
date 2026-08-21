package com.openbake.drop.infrastructure.scheduler;

import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.service.DropEnterService;
import com.openbake.drop.application.service.DropService;
import com.openbake.drop.application.service.DropStockSyncService;
import com.openbake.drop.application.cache.TodayDropCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DropScheduler {

    private final DropService dropService;
    private final DropEnterService dropEnterService;
    private final TodayDropCache todayDropCache;
    private final DropStockSyncService dropStockSyncService;

    // 서버 기동 시 당일 드롭 정보를 1회 캐싱 (자정 스케줄을 못 탄 채로 기동될 수 있으므로)
    @PostConstruct
    void init() {
        todayDropCache.refresh();
    }

    // 매일 자정에 1회만 DB를 조회해 오늘의 드롭 정보를 캐싱
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshTodayDrop() {
        todayDropCache.refresh();
    }

    // 200ms마다 실행되지만 DB에는 접근하지 않고, 캐싱된 시간 정보로만 진행 중 여부를 판단한다.
    // 상태 전환은 tryMarkStarted/tryMarkEnded가 CAS로 막아 드롭당 정확히 1회만 일어난다.
    @Scheduled(fixedRate = 200)
    public void processDropLifecycle() {
        LocalDateTime now = LocalDateTime.now();
        for(CachedDrop drop : todayDropCache.get()){
            if (now.isAfter(drop.dropEnd())) {
                if (drop.tryMarkEnded()) {
                    dropService.changeDropStatusCompleted(drop.dropId());
                    dropStockSyncService.finalizeStock(drop);
                    // 입장만 하고 구매로 이어지지 않은 진입 내역을 마감 시점에 한 번만 정리한다.
                    dropEnterService.expireRemainingEntries(drop.dropId());
                }
                continue;
            }

            if (now.isBefore(drop.dropStart())) {
                continue;
            }

            if (drop.tryMarkStarted()) {
                dropService.changeDropStatusActive(drop.dropId());
                // 재고 카운터가 없으면 요청이 전부 fail-closed 로 거부되므로 ACTIVE 전환과 같은 시점에 워밍업한다.
                dropStockSyncService.warmUp(drop);
            }
        }
    }

    // 진행 중인 드롭의 Redis 재고를 DB에 반영한다. 드롭당 UPDATE 1회라 경합이 없다.
    // 화면에 보이는 잔여 수량의 신선도를 결정하므로 짧은 주기를 유지한다.
    @Scheduled(fixedRate = 2000)
    public void syncDropStock() {
        LocalDateTime now = LocalDateTime.now();
        for (CachedDrop drop : todayDropCache.get()) {
            if (now.isBefore(drop.dropStart()) || now.isAfter(drop.dropEnd())) {
                continue;
            }

            dropStockSyncService.sync(drop);
        }
    }

    // 재고 카운터와 drop_entry 합계의 대조. 로그만 남기는 관측 로직이고
    // 집계 스캔이라 참여자가 늘수록 무거워지므로, 동기화보다 훨씬 긴 주기로 돈다.
    @Scheduled(fixedRate = 30000)
    public void checkDropStockDrift() {
        LocalDateTime now = LocalDateTime.now();
        for (CachedDrop drop : todayDropCache.get()) {
            if (now.isBefore(drop.dropStart()) || now.isAfter(drop.dropEnd())) {
                continue;
            }

            dropStockSyncService.checkDrift(drop);
        }
    }
}