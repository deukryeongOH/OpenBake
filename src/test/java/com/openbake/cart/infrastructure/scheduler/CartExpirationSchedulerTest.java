package com.openbake.cart.infrastructure.scheduler;

import com.openbake.cart.application.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CartExpirationSchedulerTest {

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartExpirationScheduler cartExpirationScheduler;

    @Test
    @DisplayName("만료 정리를 현재 시각 기준으로 위임한다")
    void expireCarts_delegatesWithCurrentTime() {
        // given
        LocalDateTime before = LocalDateTime.now();
        given(cartService.expireCarts(any())).willReturn(2);

        // when
        cartExpirationScheduler.expireCarts();

        // then
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(cartService).expireCarts(captor.capture());

        //스케줄러가 시각을 직접 만들어 넘기므로, 호출 전후 사이의 값인지만 확인한다.
        assertThat(captor.getValue())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("정리할 장바구니가 없어도 예외 없이 끝난다")
    void expireCarts_nothingToExpire() {
        // given
        given(cartService.expireCarts(any())).willReturn(0);

        // when
        cartExpirationScheduler.expireCarts();

        // then
        verify(cartService).expireCarts(any());
    }
}
