package com.openbake.cart.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class) //@CreatedDate 설정.
//한 장바구니에 같은 상품은 항상 한 행이다. 같은 상품을 또 담으면 수량을 합치므로
//서비스 로직이 새더라도 DB 가 마지막으로 막아준다.
@Table(
        name = "cart_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_product",
                columnNames = {"cart_id", "product_id"}
        )
)
@Getter
@NoArgsConstructor
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long cartItemId;

    @ManyToOne(fetch = FetchType.LAZY) //LAZY 지연로딩
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(nullable = false)
    private Long productId;

    //판매자 상호명. 담을 때의 값이다.
    //조회는 최신 상호명을 판매자에서 다시 읽으므로 평소에는 이 값을 쓰지 않고,
    //상품이 삭제돼 sellerId 를 알 수 없을 때만 마지막 단서로 쓴다.
    //판매자를 못 찾는 경우가 있어 null 을 허용한다.
    private String bakeryName;

    @Column(nullable = false)
    private int quantity;

    //담을 때는 선택하지 않아도 된다. 주문으로 넘어갈 때 반드시 채워져 있어야 한다.
    private LocalDate pickUpDate;

    //담을 때의 단가. 결제 금액의 근거가 아니라 "얼마에서 얼마로 바뀌었는지" 비교용 기준값이다.
    //결제 금액은 언제나 조회·결제 시점의 최신 가격으로 계산한다.
    //이 컬럼이 생기기 전에 담긴 항목은 기준값이 없으므로 null 을 허용한다(변동 없음으로 본다).
    private Integer addedPrice;

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; //날짜 + 시각

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt; //갱신 시각

    //정적 팩토리 메서드
    public static CartItem create(Long productId, String bakeryName, int quantity,
                                  LocalDate pickUpDate, Integer addedPrice) {
        CartItem item = new CartItem();
        item.productId = productId;
        item.bakeryName = bakeryName;
        item.quantity = quantity;
        item.pickUpDate = pickUpDate;
        item.addedPrice = addedPrice;
        return item;
    }

    /**
     * 담을 때 가격 대비 지금 가격이 달라졌는지.
     * 기준값이 없는(예전에 담긴) 항목은 비교할 게 없으므로 변동 없음으로 본다.
     */
    public boolean isPriceChanged(int currentPrice) {
        return addedPrice != null && addedPrice != currentPrice;
    }

    //수량 변경. 장바구니 페이지에서 직접 고칠 수 있다.
    public void updateQuantity(int quantity) {
        this.quantity = quantity;
    }

    //픽업 날짜 선택·변경. 장바구니 페이지에서 다시 고를 수 있다.
    public void updatePickUpDate(LocalDate pickUpDate) {
        this.pickUpDate = pickUpDate;
    }

    //같은 상품을 다시 담았을 때. 수량은 더하고 픽업일은 새로 고른 값으로 덮어쓴다.
    //이번에 픽업일을 고르지 않았으면(null) 기존 선택을 지우지 않고 그대로 둔다.
    //
    //상호명도 이번에 읽은 값으로 갱신한다. 이 값은 상품이 삭제됐을 때 쓰는 마지막 단서라
    //최근에 확인한 이름일수록 정확하다. 다만 판매자를 못 찾아 null 이 들어온 경우에는
    //덮어쓰지 않는다. 단서가 남아 있는데 지우면 화면에 아무것도 보여줄 수 없다.
    //
    //비교 기준 가격은 이번에 담은 가격으로 갱신한다. 사용자가 방금 그 가격을 보고 담았으므로
    //이전 가격과의 차이를 계속 알려주는 건 맞지 않는다.
    void merge(String newBakeryName, int addedQuantity, LocalDate newPickUpDate, Integer newAddedPrice) {
        this.quantity += addedQuantity;
        if (newBakeryName != null) {
            this.bakeryName = newBakeryName;
        }
        if (newPickUpDate != null) {
            this.pickUpDate = newPickUpDate;
        }
        this.addedPrice = newAddedPrice;
    }

    //연관관계 편의 메서드. Cart.addItem 에서만 호출한다.
    void setCart(Cart cart) {
        this.cart = cart;
    }
}
