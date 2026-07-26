package com.openbake.cart.domain;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class) //@CreatedDate 설정.
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor

public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long cartId;

    @Column(nullable = false, unique = true)
    private Long memberId;

    @OneToOne(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private CartItem items;

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; //날짜 + 시각

    @Column(nullable = false)
    private LocalDateTime expiresAt; //만료 시각. 값은 서비스에서 주입

    private LocalDate pickupDate; //픽업 날짜만 필요.

    //정적 팩토리 메서드
    public static Cart create(Long memberId, LocalDateTime expiresAt) {
        Cart cart = new Cart();
        cart.memberId = memberId;
        cart.expiresAt = expiresAt;
        return cart;
    }

    //장바구니에 장바구니 아이템 더하기, 우리 로직에서는 하나의 장바구니에 하나의 아이템(종류)만 들어갈 수 있다.
    public void addItem(CartItem item) {
        if (items != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "장바구니에 하나의 드롭만 담을 수 있습니다.");
        }
        this.items = item;
        items.setCart(this); //너는 이 장바구니거야.
    }

    //아이템 삭제
    public void removeItem() {
        if (this.items != null) {
            this.items.setCart(null);
            this.items = null;
        }
    }
    //만료 여부. 기준 시각을 밖에서 받아 조회/삭제/만료 배치가 모두 이 메서드를 쓴다.
    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    //픽업 날짜 선택. 재선택 시 기존 값을 덮어쓴다.
    public void updatePickupDate(LocalDate pickupDate) {
        this.pickupDate = pickupDate;
    }
}