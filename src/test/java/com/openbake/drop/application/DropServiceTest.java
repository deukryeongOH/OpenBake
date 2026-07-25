package com.openbake.drop.application;

import com.openbake.drop.domain.*;
import com.openbake.drop.presentation.dto.DropProductInfoRequest;
import com.openbake.drop.application.dto.DropProductInfoResponse;
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
import java.util.Set;

import static com.openbake.drop.domain.DropStatus.UPCOMING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;


import static org.mockito.BDDMockito.given;
@ExtendWith(MockitoExtension.class)
class DropServiceTest {

    @Mock
    private DropRepository dropRepository;

    @Mock
    private DropInventoryRepository dropInventoryRepository; // 추가된 Repository Mock

    @InjectMocks
    private DropService dropService;

    private DropProductInfoRequest request;

    @BeforeEach
    void setUp() {
        Set<LocalDate> pickUpDates = Set.of(
                LocalDate.parse("2028-08-01"),
                LocalDate.parse("2028-08-02"),
                LocalDate.parse("2028-08-03")
        );

        request = new DropProductInfoRequest(
                "두쫀쿠",
                "원물 맛이 많이 나요.",
                "C:\\Users\\deukr\\OneDrive\\바탕 화면\\두쫀쿠.jpg",
                pickUpDates,
                LocalDateTime.parse("2028-07-25T13:00:00"),
                LocalDateTime.parse("2028-07-25T14:00:00"),
                5,
                8000,
                200
        );
    }

    @Test
    @DisplayName("드롭 상품 등록 성공 - Drop과 DropInventory가 정상 저장되어야 한다")
    void registerDropProduct_Success() {
        // given
        Long sellerId = 1L;

        // 1. Mock Drop 엔티티 준비 (DB 저장 후 ID가 할당된 상태 모킹)
        DropProduct dropProduct = DropProduct.builder()
                .name(request.name())
                .description(request.description())
                .imageUrl(request.imageUrl())
                .price(request.price())
                .build();

        Drop savedDrop = Drop.builder()
                .dropStatus(UPCOMING)
                .pickUpAvailableDates(request.pickUpAvailableDates())
                .dropProduct(dropProduct)
                .limitQuantity(request.limitQuantity())
                .dropStart(request.dropStart())
                .dropEnd(request.dropEnd())
                .sellerId(sellerId)
                .build();

        ReflectionTestUtils.setField(savedDrop, "id", 100L);

        // 2. Mock DropInventory 엔티티 준비
        DropInventory savedDropInventory = DropInventory.builder()
                .dropId(100L)
                .totalQuantity(request.totalQuantity())
                .remainQuantity(request.totalQuantity())
                .build();

        // 3. Repository 스터빙 (Stubbing)
        given(dropRepository.save(any())).willReturn(savedDrop);
        given(dropInventoryRepository.save(any(DropInventory.class))).willReturn(savedDropInventory);

        // when
        DropProductInfoResponse response = dropService.registerDropProduct(request, sellerId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo(request.name());
        assertThat(response.description()).isEqualTo(request.description());
        assertThat(response.dropStart()).isEqualTo(request.dropStart());
        assertThat(response.dropEnd()).isEqualTo(request.dropEnd());
        assertThat(response.limitQuantity()).isEqualTo(request.limitQuantity());
        assertThat(response.price()).isEqualTo(request.price());
        assertThat(response.totalQuantity()).isEqualTo(request.totalQuantity());
        assertThat(response.imageUrl()).isEqualTo(request.imageUrl());
        assertThat(response.dropStatus()).isEqualTo(UPCOMING);
        assertThat(response.dropId()).isEqualTo(100L);
        assertThat(response.remainQuantity()).isEqualTo(request.totalQuantity());

        // 4. 실제 DB 저장 메소드가 호출되었는지 검증
        verify(dropRepository).save(any(Drop.class));
        verify(dropInventoryRepository).save(any(DropInventory.class));
    }
}