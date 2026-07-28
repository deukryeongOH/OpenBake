package com.openbake.drop.application;

import com.openbake.drop.application.queue.InMemoryQueueManager;
import com.openbake.drop.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DropLockServiceTest {

    @Mock
    private DropRepository dropRepository;

    @Mock
    private DropService dropService;

    @Mock
    private DropInventoryRepository dropInventoryRepository;

    @Mock
    private DropEntryRepository dropEntryRepository;

    @Mock
    private InMemoryQueueManager queueManager;

    @InjectMocks
    private DropLockService dropLockService;

    private final Long dropId = 1L;
    private final Long memberId = 10L;

    private DropEntry enteredEntry;
    private DropInventory dropInventory;
    private Drop drop;

    @BeforeEach
    void setUp() {
        enteredEntry = DropEntry.builder()
                .dropId(dropId)
                .memberId(memberId)
                .entryStatus(EntryStatus.ENTERED)
                .build();

        dropInventory = DropInventory.builder()
                .dropId(dropId)
                .totalQuantity(100)
                .remainQuantity(100)
                .build();

        DropProduct dropProduct = DropProduct.builder()
                .name("두쫀쿠")
                .description("원물 맛이 많이 나요.")
                .imageUrl("image.jpg")
                .price(8000)
                .build();

        LocalDateTime now = LocalDateTime.now();
        drop = Drop.builder()
                .dropStatus(DropStatus.ACTIVE)
                .dropProduct(dropProduct)
                .pickUpAvailableDates(Set.of(now.toLocalDate().plusDays(3)))
                .limitQuantity(5)
                .dropStart(now.plusMinutes(1))
                .dropEnd(now.plusMinutes(30))
                .sellerId(1L)
                .build();
    }

    @Test
    @DisplayName("재고 차감 성공 - 재고가 줄고 DropEntry 상태가 RESERVED로 변경된다")
    void decreaseQuantity_Success() {
        // given
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));
        given(dropInventoryRepository.findByDropId(dropId)).willReturn(dropInventory);

        // when
        dropLockService.decreaseQuantity(dropId, memberId, 3);

        // then
        assertThat(dropInventory.getRemainQuantity()).isEqualTo(97);
        assertThat(enteredEntry.getEntryStatus()).isEqualTo(EntryStatus.RESERVED);
    }

    @Test
    @DisplayName("대기열 통과 상태 검증 성공")
    void checkEntryStatus_Success() {
        // given
        given(dropEntryRepository.findByDropIdAndMemberId(dropId, memberId))
                .willReturn(Optional.of(enteredEntry));

        // when & then (예외 없이 통과)
        dropLockService.checkEntryStatus(dropId, memberId);
    }

    @Test
    @DisplayName("1인당 제한 수량 검증 성공")
    void checkLimitQuantityPerPerson_Success() {
        // given
        given(dropRepository.findById(dropId)).willReturn(Optional.of(drop));

        // when & then (예외 없이 통과)
        dropLockService.checkLimitQuantityPerPerson(dropId, 3);
    }
}
