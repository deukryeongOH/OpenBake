package com.openbake.cart.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Entity
@EntityListeners(AuditingEntityListener.class) //@CreatedDate 설정.
@Table(name = "carts")
@Getter
@NoArgsConstructor

public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long cartId;

    @Column(nullable = false, unique = true)
    private Long memberId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("cartItemId DESC")
    private List<CartItem> items = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; //날짜 + 시각

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt; //갱신 시각

    //정적 팩토리 메서드
    public static Cart create(Long memberId) {
        Cart cart = new Cart();
        cart.memberId = memberId;
        return cart;
    }

    //장바구니에 아이템 담기. 여러 종류를 담을 수 있되 한 상품은 항상 한 행이다.
    //이미 담은 상품이면 수량을 더하고 픽업일을 새로 고른 값으로 덮어쓴다.
    public void addItem(CartItem item) {
        findItem(item.getProductId())
                .ifPresentOrElse(
                        existing -> existing.merge(
                                item.getBakeryName(), item.getQuantity(),
                                item.getPickUpDate(), item.getAddedPrice()),
                        () -> {
                            items.add(item);
                            item.setCart(this); //너는 이 장바구니거야.
                        }
                );
    }

    //담긴 상품 찾기. 재고 검증에 쓸 합산 수량을 서비스가 미리 계산할 때도 쓴다.
    public Optional<CartItem> findItem(Long productId) {
        return items.stream()
                .filter(item -> Objects.equals(item.getProductId(), productId))
                .findFirst();
    }

    //장바구니에서 아이템 빼기. orphanRemoval 이 cart_items 행을 지운다.
    public void removeItem(CartItem item) {
        items.remove(item);
    }

    //장바구니 비우기. 항목만 지우고 장바구니 행은 남긴다.
    public void clearItems() {
        items.clear();
    }
}