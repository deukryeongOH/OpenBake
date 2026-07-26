package com.openbake.seller.application;

import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.seller.domain.ApplicationStatus;
import com.openbake.seller.domain.Seller;
import com.openbake.seller.domain.SellerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CurrentSellerProviderTest {

    @Mock
    private CurrentMemberProvider currentMemberProvider;

    @Mock
    private SellerRepository sellerRepository;

    @InjectMocks
    private CurrentSellerProvider currentSellerProvider;

    @Test
    @DisplayName("현재 회원이 승인된 판매자면 sellerId를 반환한다")
    void getSellerId_approved() {
        given(currentMemberProvider.getId()).willReturn(1L);
        Seller seller = new Seller(1L, "세종베이커리", "123-45-67890", "서울시",
                "이세종", true, "088", "1101234567", "이세종", true);
        ReflectionTestUtils.setField(seller, "id", 10L);
        ReflectionTestUtils.setField(seller, "applicationStatus", ApplicationStatus.APPROVED);
        given(sellerRepository.findByMemberId(1L)).willReturn(Optional.of(seller));

        Optional<Long> sellerId = currentSellerProvider.getSellerId();

        assertThat(sellerId).contains(10L);
    }

    @Test
    @DisplayName("판매자 신청이 아직 pending/rejected 상태면 빈 값을 반환한다")
    void getSellerId_notApproved() {
        given(currentMemberProvider.getId()).willReturn(1L);
        Seller seller = new Seller(1L, "세종베이커리", "123-45-67890", "서울시",
                "이세종", true, "088", "1101234567", "이세종", true);
        ReflectionTestUtils.setField(seller, "id", 10L);
        given(sellerRepository.findByMemberId(1L)).willReturn(Optional.of(seller));

        Optional<Long> sellerId = currentSellerProvider.getSellerId();

        assertThat(sellerId).isEmpty();
    }

    @Test
    @DisplayName("현재 회원이 아직 판매자가 아니면 빈 값을 반환한다")
    void getSellerId_notSeller() {
        given(currentMemberProvider.getId()).willReturn(1L);
        given(sellerRepository.findByMemberId(1L)).willReturn(Optional.empty());

        Optional<Long> sellerId = currentSellerProvider.getSellerId();

        assertThat(sellerId).isEmpty();
    }
}
