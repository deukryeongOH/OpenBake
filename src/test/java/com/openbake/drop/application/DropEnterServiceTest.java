package com.openbake.drop.application;

import com.openbake.drop.application.dto.ConfirmEntryResult;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.application.dto.QueueRankResult;
import com.openbake.drop.application.port.CurrentMemberPort;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.queue.QueueManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DropEnterServiceTest {

    @Mock
    private DropEntryRepository dropEntryRepository;

    @Mock
    private QueueManager queueManager;

    @Mock
    private DropRepository dropRepository;

    @Mock
    private CurrentMemberPort currentMemberPort;

    @Mock
    private ProductPort productPort;

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
    @DisplayName("대기열 진입 성공 - 대기 순번을 반환한다")
    void enterQueue_Success() {
        // given
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(activeDrop));
        given(dropEntryRepository.existsByDropIdAndMemberIdAndEntryStatusIn(
                eq(dropId), eq(memberId), any(List.class))).willReturn(false);
        given(queueManager.enqueue(dropId, memberId)).willReturn(3L);

        // when
        QueueRankResult result = dropEnterService.enterQueue(dropId);

        // then
        assertThat(result.rank()).isEqualTo(3L);
        assertThat(result.status()).isEqualTo("WAITING");
        verify(queueManager).enqueue(dropId, memberId);
    }

    @Test
    @DisplayName("대기열 순번 조회 성공")
    void getRank_Success() {
        // given
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(queueManager.getRank(dropId, memberId)).willReturn(0L);

        // when
        QueueRankResult result = dropEnterService.getRank(dropId);

        // then
        assertThat(result.rank()).isEqualTo(0L);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("입장 확정 성공 - 대기열 권한을 검증하고 진입 내역을 저장한다")
    void confirmEntry_Success() {
        // given
        given(currentMemberPort.getCurrentMemberId()).willReturn(memberId);
        given(queueManager.isActive(dropId, memberId)).willReturn(true);
        given(dropRepository.findById(dropId)).willReturn(Optional.of(activeDrop));

        DropProductInfoResult productInfo = DropProductInfoResult.of(
                "두쫀쿠", "원물 맛이 많이 나요.", "image.jpg",
                Set.of(LocalDate.of(2028, 7, 28)), 8000, 100, 97, 1L, productId);
        given(productPort.getProductInfo(productId)).willReturn(productInfo);

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
        verify(queueManager).removeActiveUser(dropId, memberId);
    }

    @Test
    @DisplayName("만료된 멤버 중 ENTERED 상태인 경우 FAILED로 전환한다")
    void failExpiredEntries_FailsEnteredEntries() {
        // given
        Long otherMemberId = 20L;
        DropEntry enteredEntry = DropEntry.builder()
                .dropId(dropId).memberId(memberId).entryStatus(EntryStatus.ENTERED).build();
        DropEntry otherEnteredEntry = DropEntry.builder()
                .dropId(dropId).memberId(otherMemberId).entryStatus(EntryStatus.ENTERED).build();

        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, otherMemberId))
                .willReturn(Optional.of(otherEnteredEntry));

        // when
        dropEnterService.failExpiredEntries(dropId, Set.of(memberId, otherMemberId));

        // then
        assertThat(enteredEntry.getEntryStatus()).isEqualTo(EntryStatus.FAILED);
        assertThat(otherEnteredEntry.getEntryStatus()).isEqualTo(EntryStatus.FAILED);
    }

    @Test
    @DisplayName("이미 RESERVED/COMPLETED 등으로 넘어간 멤버는 건드리지 않는다")
    void failExpiredEntries_SkipsNonEnteredStatus() {
        // given
        DropEntry reservedEntry = DropEntry.builder()
                .dropId(dropId).memberId(memberId).entryStatus(EntryStatus.RESERVED).build();
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(reservedEntry));

        // when
        dropEnterService.failExpiredEntries(dropId, Set.of(memberId));

        // then
        assertThat(reservedEntry.getEntryStatus()).isEqualTo(EntryStatus.RESERVED);
    }

    @Test
    @DisplayName("confirmEntry를 한 번도 호출하지 않아 DropEntry가 없는 멤버는 예외 없이 건너뛴다")
    void failExpiredEntries_SkipsWhenNoDropEntryExists() {
        // given
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.empty());

        // when & then (예외 없이 통과)
        dropEnterService.failExpiredEntries(dropId, Set.of(memberId));
    }
}