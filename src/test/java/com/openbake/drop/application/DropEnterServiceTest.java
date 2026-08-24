package com.openbake.drop.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.cache.TodayDropCache;
import com.openbake.drop.application.dto.ConfirmEntryResult;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.application.port.CurrentMemberPort;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.port.StockReservationPort;
import com.openbake.drop.application.service.DropEnterService;
import com.openbake.drop.domain.*;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.entity.DropEntry;
import com.openbake.drop.domain.repository.DropEntryRepository;
import com.openbake.drop.domain.repository.DropRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DropEnterServiceTest {

    @Mock
    private DropEntryRepository dropEntryRepository;

    @Mock
    private DropRepository dropRepository;

    @Mock
    private CurrentMemberPort currentMemberPort;

    @Mock
    private ProductPort productPort;

    @Mock
    private TodayDropCache todayDropCache;

    @Mock
    private StockReservationPort stockReservationPort;

    @InjectMocks
    private DropEnterService dropEnterService;

    private final Long dropId = 1L;
    private final Long memberId = 10L;
    private final Long productId = 500L;

    private Drop activeDrop;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        activeDrop = Drop.builder()
                .dropStatus(DropStatus.ACTIVE)
                .productId(productId)
                .limitQuantity(5)
                // 빌더는 과거 시각을 허용하지 않고 시작 시각이 TimeSlot(9/11/13/15/17시)에 맞아야 하므로
                // 우선 미래의 슬롯 시각으로 생성한 뒤, 아래에서 현재 시각 기준으로 되돌린다.
                .dropStart(LocalDateTime.of(2028, 7, 25, 9, 0))
                .dropEnd(LocalDateTime.of(2028, 7, 25, 10, 0))
                .build();

        ReflectionTestUtils.setField(activeDrop, "id", dropId);
        // 진행 시간 검증(isAccessible)을 통과시키기 위해 시작/종료 시각을 현재 시각 기준으로 조정
        // 드롭 진행 시간은 항상 1시간이므로 dropEnd도 dropStart + 1시간으로 맞춘다
        ReflectionTestUtils.setField(activeDrop, "dropStart", now.minusMinutes(10));
        ReflectionTestUtils.setField(activeDrop, "dropEnd", now.minusMinutes(10).plusHours(1));
    }

    private CachedDrop cachedDrop() {
        return new CachedDrop(LocalDate.now(), dropId, productId,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(30),
                new AtomicBoolean(true), new AtomicBoolean(false), 5,
                "두쫀쿠", "원물 맛이 많이 나요.", "image.jpg", 8000, Set.of(LocalDate.of(2028, 7, 28)));
    }

    // 정상 경로: 상품 정보는 캐시, 잔여 수량은 Redis에서 온다 (DB 상품 조회 없음)
    private void givenProductFromCacheAndRedis() {
        given(stockReservationPort.peek(dropId)).willReturn(97L);
        given(todayDropCache.find(dropId)).willReturn(Optional.of(cachedDrop()));
    }

    private DropProductInfoResult productInfo() {
        return DropProductInfoResult.of(
                "두쫀쿠", "원물 맛이 많이 나요.", "image.jpg",
                Set.of(LocalDate.of(2028, 7, 28)), 8000, 100, 97, 1L, productId);
    }

    @Test
    @DisplayName("오늘 진행하는 드롭 ID 리스트 조회 성공")
    void getTodayDropIds_Success() {
        // given
        given(dropRepository.findListByDropDate(any()))
                .willReturn(List.of(activeDrop));

        // when
        List<Long> result = dropEnterService.getTodayDropIds();

        // then
        assertThat(result).containsExactly(dropId);
    }

    @Test
    @DisplayName("입장 확정 성공 - 진입 내역을 저장한다")
    void confirmEntry_Success() {
        // given
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(activeDrop));
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)).willReturn(Optional.empty());
        givenProductFromCacheAndRedis();

        DropProductInfoResult productInfo = productInfo();
        given(dropEntryRepository.save(any(DropEntry.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        ConfirmEntryResult result = dropEnterService.confirmEntry(dropId);

        // then
        assertThat(result.name()).isEqualTo(productInfo.name());
        assertThat(result.description()).isEqualTo(productInfo.description());
        assertThat(result.imageUrl()).isEqualTo(productInfo.imageUrl());
        assertThat(result.price()).isEqualTo(productInfo.price());
        assertThat(result.limitQuantity()).isEqualTo(activeDrop.getLimitQuantity());
        assertThat(result.remainQuantity()).isEqualTo(productInfo.remainQuantity());
        assertThat(result.pickupDates()).isEqualTo(productInfo.pickUpAvailableDates());

        verify(dropEntryRepository).save(any(DropEntry.class));
    }

    @Test
    @DisplayName("입장 확정 실패 - 드롭 진행 기간이 아니면 거부한다 (대기열이 하던 시간 검증을 이관)")
    void confirmEntry_OutsideDropWindow_Throws() {
        // given
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(activeDrop, "dropStart", now.plusMinutes(10));
        ReflectionTestUtils.setField(activeDrop, "dropEnd", now.plusMinutes(70));

        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(activeDrop));

        // when & then
        assertThatThrownBy(() -> dropEnterService.confirmEntry(dropId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DROP_NOT_ACTIVE);

        verify(dropEntryRepository, never()).save(any(DropEntry.class));
    }

    @Test
    @DisplayName("입장 확정 실패 - 이미 재고를 선점했거나 구매를 마친 유저는 거부한다 (대기열이 하던 중복 차단을 이관)")
    void confirmEntry_AlreadyReservedOrCompleted_Throws() {
        // given
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(activeDrop));
        DropEntry reserved = DropEntry.builder()
                .dropId(dropId).memberId(memberId).entryStatus(EntryStatus.RESERVED).build();
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(reserved));

        // when & then
        assertThatThrownBy(() -> dropEnterService.confirmEntry(dropId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_ENTERED);

        verify(dropEntryRepository, never()).save(any(DropEntry.class));
    }

    @Test
    @DisplayName("입장 확정 실패 - 존재하지 않는 드롭이면 거부한다")
    void confirmEntry_DropNotFound_Throws() {
        // given
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(dropRepository.findById(dropId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> dropEnterService.confirmEntry(dropId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DROP_NOT_FOUND);
    }

    @Test
    @DisplayName("재입장이면 새 엔트리를 만들지 않고 기존 엔트리를 ENTERED로 되돌린다")
    void confirmEntry_ReEntry_ReusesExistingEntry() {
        // given
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(activeDrop));
        givenProductFromCacheAndRedis();

        DropEntry existing = DropEntry.builder()
                .dropId(dropId).memberId(memberId).entryStatus(EntryStatus.FAILED).build();
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(existing));
        given(dropEntryRepository.save(any(DropEntry.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        dropEnterService.confirmEntry(dropId);

        // then
        assertThat(existing.getEntryStatus()).isEqualTo(EntryStatus.ENTERED);
        verify(dropEntryRepository).save(existing);
    }

    @Test
    @DisplayName("이미 ENTERED인 유저가 다시 호출하면 저장 없이 정보만 돌려준다 (반복 호출로 쓰기가 쌓이지 않는다)")
    void confirmEntry_AlreadyEntered_DoesNotWriteAgain() {
        // given
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(activeDrop));
        givenProductFromCacheAndRedis();

        DropEntry alreadyEntered = DropEntry.builder()
                .dropId(dropId).memberId(memberId).entryStatus(EntryStatus.ENTERED).build();
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(alreadyEntered));

        // when
        ConfirmEntryResult result = dropEnterService.confirmEntry(dropId);

        // then
        assertThat(result.limitQuantity()).isEqualTo(activeDrop.getLimitQuantity());
        verify(dropEntryRepository, never()).save(any(DropEntry.class));
    }

    @Test
    @DisplayName("정상 경로에서는 상품 정보를 DB가 아니라 캐시와 Redis에서 읽는다")
    void confirmEntry_ReadsProductFromCacheAndRedis() {
        // given
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(activeDrop));
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)).willReturn(Optional.empty());
        givenProductFromCacheAndRedis();
        given(dropEntryRepository.save(any(DropEntry.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        ConfirmEntryResult result = dropEnterService.confirmEntry(dropId);

        // then: 잔여 수량은 Redis 실시간 값이고, 표시 정보는 캐시 값이다
        assertThat(result.remainQuantity()).isEqualTo(97);
        assertThat(result.name()).isEqualTo("두쫀쿠");
        // 상품·재고·픽업일 3개 쿼리를 태우던 경로를 타지 않는다
        verify(productPort, never()).getProductInfo(any());
    }

    @Test
    @DisplayName("Redis 카운터가 없으면 기존 DB 경로로 폴백한다")
    void confirmEntry_RedisMissing_FallsBackToDb() {
        // given
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(activeDrop));
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)).willReturn(Optional.empty());
        given(stockReservationPort.peek(dropId)).willReturn(null);
        given(productPort.getProductInfo(productId)).willReturn(productInfo());
        given(dropEntryRepository.save(any(DropEntry.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        ConfirmEntryResult result = dropEnterService.confirmEntry(dropId);

        // then
        assertThat(result.remainQuantity()).isEqualTo(97);
        verify(productPort).getProductInfo(productId);
    }

    @Test
    @DisplayName("마감 정리는 드롭당 UPDATE 한 번으로 ENTERED 잔여 엔트리를 실패 처리한다")
    void expireRemainingEntries_DelegatesToSingleUpdate() {
        // given
        given(dropEntryRepository.expireEnteredEntries(dropId)).willReturn(3);

        // when
        int expired = dropEnterService.expireRemainingEntries(dropId);

        // then
        assertThat(expired).isEqualTo(3);
        verify(dropEntryRepository).expireEnteredEntries(dropId);
    }
}