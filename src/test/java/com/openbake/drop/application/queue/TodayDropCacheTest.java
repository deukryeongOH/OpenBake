package com.openbake.drop.application.queue;

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
import java.util.Optional;
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

    @Test
    @DisplayName("오늘 진행되는 드롭이 있으면 캐시에 dropId/시작/종료 시각을 채운다")
    void refresh_WithDropToday_CachesDropInfo() {
        // given
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);

        DropProduct dropProduct = DropProduct.builder()
                .name("두쫀쿠").description("d").imageUrl("i.jpg").price(8000).build();
        Drop drop = Drop.builder()
                .dropStatus(DropStatus.UPCOMING)
                .dropProduct(dropProduct)
                .pickUpAvailableDates(Set.of(end.toLocalDate().plusDays(1)))
                .limitQuantity(1)
                .dropStart(start)
                .dropEnd(end)
                .sellerId(1L)
                .build();
        ReflectionTestUtils.setField(drop, "id", 100L);

        given(dropRepository.findByDropStartBetween(any(), any())).willReturn(Optional.of(drop));

        // when
        todayDropCache.refresh();

        // then
        TodayDropCache.CachedDrop cached = todayDropCache.get();
        assertThat(cached.dropId()).isEqualTo(100L);
        assertThat(cached.dropStart()).isEqualTo(start);
        assertThat(cached.dropEnd()).isEqualTo(end);
    }

    @Test
    @DisplayName("오늘 진행되는 드롭이 없으면 캐시를 비운다")
    void refresh_WithNoDropToday_CachesEmpty() {
        // given
        given(dropRepository.findByDropStartBetween(any(), any())).willReturn(Optional.empty());

        // when
        todayDropCache.refresh();

        // then
        assertThat(todayDropCache.get().dropId()).isNull();
    }

    @Test
    @DisplayName("tryMarkStarted는 최초 1회만 true를 반환한다")
    void tryMarkStarted_ReturnsTrueOnlyOnce() {
        assertThat(todayDropCache.tryMarkStarted()).isTrue();
        assertThat(todayDropCache.tryMarkStarted()).isFalse();
        assertThat(todayDropCache.tryMarkStarted()).isFalse();
    }

    @Test
    @DisplayName("tryMarkEnded는 최초 1회만 true를 반환한다")
    void tryMarkEnded_ReturnsTrueOnlyOnce() {
        assertThat(todayDropCache.tryMarkEnded()).isTrue();
        assertThat(todayDropCache.tryMarkEnded()).isFalse();
        assertThat(todayDropCache.tryMarkEnded()).isFalse();
    }

    @Test
    @DisplayName("refresh를 다시 호출하면 이전에 마킹된 tryMarkStarted/tryMarkEnded 플래그가 초기화된다")
    void refresh_ResetsMarkFlags() {
        // given: 드롭이 이미 시작/종료 처리된 상태였다가
        given(dropRepository.findByDropStartBetween(any(), any())).willReturn(Optional.empty());
        todayDropCache.tryMarkStarted();
        todayDropCache.tryMarkEnded();

        // when: 드롭 등록/수정으로 캐시가 다시 갱신되면
        todayDropCache.refresh();

        // then: 플래그가 초기화되어 다시 한번 전환을 트리거할 수 있어야 한다
        assertThat(todayDropCache.tryMarkStarted()).isTrue();
        assertThat(todayDropCache.tryMarkEnded()).isTrue();
    }
}