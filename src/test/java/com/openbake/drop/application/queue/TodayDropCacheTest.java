package com.openbake.drop.application.queue;

import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.cache.TodayDropCache;
import com.openbake.drop.domain.Drop;
import com.openbake.drop.domain.DropProduct;
import com.openbake.drop.domain.DropRepository;
import com.openbake.drop.domain.DropStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TodayDropCacheTest {

    @Mock
    private DropRepository dropRepository;

    @InjectMocks
    private TodayDropCache todayDropCache;

    private static Drop drop(Long id, LocalDateTime start, LocalDateTime end, Long sellerId) {
        DropProduct dropProduct = DropProduct.builder()
                .name("두쫀쿠").description("d").imageUrl("i.jpg").price(8000).build();
        Drop drop = Drop.builder()
                .dropStatus(DropStatus.UPCOMING)
                .dropProduct(dropProduct)
                .pickUpAvailableDates(Set.of(end.toLocalDate().plusDays(1)))
                .limitQuantity(1)
                .dropStart(start)
                .dropEnd(end)
                .sellerId(sellerId)
                .build();
        ReflectionTestUtils.setField(drop, "id", id);
        return drop;
    }

    @Test
    @DisplayName("오늘 진행되는 드롭이 있으면 캐시에 dropId/시작/종료 시각을 채운다")
    void refresh_WithDropToday_CachesDropInfo() {
        // given
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);

        given(dropRepository.findListByDropDate(any())).willReturn(List.of(drop(100L, start, end, 1L)));

        // when
        todayDropCache.refresh();

        // then
        List<CachedDrop> cached = todayDropCache.get();
        assertThat(cached).hasSize(1);
        assertThat(cached.get(0).dropId()).isEqualTo(100L);
        assertThat(cached.get(0).dropStart()).isEqualTo(start);
        assertThat(cached.get(0).dropEnd()).isEqualTo(end);
    }

    @Test
    @DisplayName("오늘 진행되는 드롭이 여러 개면 캐시에도 전부 담긴다")
    void refresh_WithMultipleDropsToday_CachesAll() {
        // given
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);

        given(dropRepository.findListByDropDate(any())).willReturn(List.of(
                drop(100L, start, end, 1L),
                drop(200L, start.plusHours(3), end.plusHours(3), 2L)
        ));

        // when
        todayDropCache.refresh();

        // then
        assertThat(todayDropCache.get())
                .extracting(CachedDrop::dropId)
                .containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    @DisplayName("오늘 진행되는 드롭이 없으면 캐시를 비운다")
    void refresh_WithNoDropToday_CachesEmpty() {
        // given
        given(dropRepository.findListByDropDate(any())).willReturn(List.of());

        // when
        todayDropCache.refresh();

        // then
        assertThat(todayDropCache.get()).isEmpty();
    }

    @Test
    @DisplayName("refresh를 다시 호출하면 새 CachedDrop이 만들어져 이전 tryMarkStarted 마킹이 초기화된다")
    void refresh_ResetsMarkFlags() {
        // given: 드롭이 이미 시작 처리(tryMarkStarted 마킹)된 상태였다가
        // (Drop 생성자가 과거 dropStart를 거부하므로 미래 시각을 쓰고, "시작됨" 상태는 마킹으로만 시뮬레이션한다)
        LocalDateTime start = LocalDateTime.now().plusMinutes(10);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        given(dropRepository.findListByDropDate(any())).willReturn(List.of(drop(100L, start, end, 1L)));

        todayDropCache.refresh();
        todayDropCache.get().get(0).tryMarkStarted();

        // when: 드롭 등록/수정으로 캐시가 다시 갱신되면
        todayDropCache.refresh();

        // then: 새로 생성된 CachedDrop은 마킹 이력이 없어 다시 한번 전환을 트리거할 수 있어야 한다
        assertThat(todayDropCache.get().get(0).tryMarkStarted()).isTrue();
    }
}
