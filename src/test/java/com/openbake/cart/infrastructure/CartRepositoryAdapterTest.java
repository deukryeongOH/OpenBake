package com.openbake.cart.infrastructure;

import com.openbake.cart.domain.Cart;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CartRepositoryAdapter 단위 테스트.
 *
 * 이 어댑터의 존재 이유는 두 가지다.
 * 영속성 예외를 도메인 에러 코드로 바꿔서 application 계층에 JPA 를 노출하지 않는 것,
 * 그리고 flush 같은 JPA 용어를 포트 밖으로 내보내지 않는 것이다.
 */
@ExtendWith(MockitoExtension.class)
class CartRepositoryAdapterTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private CartJpaRepository cartJpaRepository;

    @InjectMocks
    private CartRepositoryAdapter adapter;

    @Test
    @DisplayName("저장은 saveAndFlush 로 즉시 반영한다")
    void save_flushesImmediately() {
        // given — 커밋까지 미루면 UNIQUE 위반을 이 자리에서 잡을 수 없다.
        Cart cart = Cart.create(MEMBER_ID);
        when(cartJpaRepository.saveAndFlush(cart)).thenReturn(cart);

        // when
        Cart saved = adapter.save(cart);

        // then
        assertThat(saved).isSameAs(cart);
        verify(cartJpaRepository).saveAndFlush(cart);
    }

    @Test
    @DisplayName("member_id UNIQUE 위반은 CA001 로 바꿔서 올린다")
    void save_translatesUniqueViolation() {
        // given — 장바구니가 없던 회원이 담기를 더블클릭해
        //         두 요청이 함께 "장바구니 없음"을 통과한 경우다.
        Cart cart = Cart.create(MEMBER_ID);
        when(cartJpaRepository.saveAndFlush(any(Cart.class)))
                .thenThrow(new DataIntegrityViolationException("uk violation"));

        // when & then — 스프링 예외가 서비스까지 새어 나가면 안 된다.
        assertThatThrownBy(() -> adapter.save(cart))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("회원으로 장바구니를 찾는다")
    void findByMemberId_delegates() {
        // given
        Cart cart = Cart.create(MEMBER_ID);
        when(cartJpaRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(cart));

        // when & then
        assertThat(adapter.findByMemberId(MEMBER_ID)).contains(cart);
    }

    @Test
    @DisplayName("장바구니가 없으면 빈 값을 돌려준다")
    void findByMemberId_returnsEmpty() {
        // given
        when(cartJpaRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        // when & then — 조회는 예외가 아니라 빈 값으로 답한다. 판단은 서비스가 한다.
        assertThat(adapter.findByMemberId(MEMBER_ID)).isEmpty();
    }

    @Test
    @DisplayName("존재 여부만 확인한다")
    void existsByMemberId_delegates() {
        // given
        when(cartJpaRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);

        // when & then
        assertThat(adapter.existsByMemberId(MEMBER_ID)).isTrue();
    }

    @Test
    @DisplayName("장바구니 행을 지운다")
    void delete_delegates() {
        // given
        Cart cart = Cart.create(MEMBER_ID);

        // when
        adapter.delete(cart);

        // then
        verify(cartJpaRepository).delete(cart);
    }
}
