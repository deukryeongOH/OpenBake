package com.openbake.cart.infrastructure;

import com.openbake.cart.domain.Cart;
import com.openbake.cart.domain.CartRepository;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CartRepositoryAdapter implements CartRepository {
    private final CartJpaRepository cartJpaRepository;

    /**
     * saveAndFlush 로 즉시 INSERT 해야 UNIQUE 위반을 이 자리에서 잡을 수 있다.
     * save 만 쓰면 ID 전략에 따라 커밋 시점으로 밀려 여기서 못 잡는다.
     */
    @Override
    public Cart save(Cart cart) {
        try {
            return cartJpaRepository.saveAndFlush(cart);
        } catch (DataIntegrityViolationException e) {
            //carts.member_id UNIQUE 위반.
            //더블클릭 등으로 두 요청이 기존 장바구니 조회를 함께 통과한 경우다.
            //선검사만으로는 동시 요청을 막을 수 없어 DB 제약이 최종 방어선이 된다.
            throw new BusinessException(ErrorCode.CART_ALREADY_EXISTS);
        }
    }

    @Override
    public Optional<Cart> findByMemberId(Long memberId) {
        return cartJpaRepository.findByMemberId(memberId);
    }

    @Override
    public boolean existsByMemberId(Long memberId) {
        return cartJpaRepository.existsByMemberId(memberId);
    }

    @Override
    public List<Cart> findAllByExpiresAtLessThanEqual(LocalDateTime now) {
        return cartJpaRepository.findAllByExpiresAtLessThanEqual(now);
    }

    @Override
    public void delete(Cart cart) {
        cartJpaRepository.delete(cart);
    }

    /**
     * delete 는 삭제를 예약만 하고 SQL 을 커밋 시점까지 미룬다.
     * flush 로 DELETE 를 먼저 내보내야 뒤따르는 INSERT 가 UNIQUE 제약에 걸리지 않는다.
     */
    @Override
    public void deleteImmediately(Cart cart) {
        cartJpaRepository.delete(cart);
        cartJpaRepository.flush();
    }

    @Override
    public void deleteAll(List<Cart> carts) {
        cartJpaRepository.deleteAll(carts);
    }
}