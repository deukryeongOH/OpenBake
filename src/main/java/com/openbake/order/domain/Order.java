package com.openbake.order.domain;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문.
 *
 * <b>결제보다 주문 생성이 먼저다.</b> PENDING 행이 Saga 앵커다.
 * 차감 멱등키는 Order가 현재 결제 시도 번호로 만든다.
 *
 * 판매자·픽업일·구매확정 상태는 이 엔티티가 아니라 {@link OrderItem} 에 있다.
 * 한 주문에 판매자가 여럿일 수 있기 때문이다. 반대로 진행 상태(orderState)는
 * 주문 전체가 한 덩어리로 움직이므로 여기 둔다 — <b>부분 취소는 없다.</b>
 *
 * 재고를 이 엔티티가 직접 건드리지는 않는다. 차감·복구 지점은 판매 형태로 갈린다
 * (GENERAL 은 결제 성공 직후 order 가, DROP 은 lock-start 에서 drop 이).
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long orderId;

    @Column(nullable = false)
    private Long memberId;

    //구매자 이름 스냅샷.
    @Column(name = "buyer_name_snapshot", nullable = false)
    private String buyerNameSnapshot;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_type", nullable = false, length = 20)
    private SalesType salesType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderState orderState;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_reason", length = 30)
    private OrderFailReason failReason;

    //주문 1건에 항목 여럿. 판매자가 섞일 수 있다.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /**
     * 진행 중 주문 슬롯. 값은 memberId 그대로이고, 끝난 주문은 null 이다.
     *
     * nullable UNIQUE 라 <b>회원당 값이 채워진 행은 최대 1개</b>다(NULL 은 UNIQUE 충돌을
     * 일으키지 않는다). 이것으로 "동시에 두 건" 주문을 막는다 — 요청 내용에서 파생한
     * 키로는 창 두 개가 부분만 겹치게 고르는 경우를 원리적으로 막을 수 없다.
     *
     * 멱등키가 아니라 슬롯이므로 이름도 idempotency_key 가 아니다.
     * Payment 멱등키와는 목적도 저장소도 다르고 payment 로 보내지 않는다.
     */
    @Column(name = "active_member_id", unique = true)
    private Long activeMemberId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    //결제 전에는 null 이다. PENDING 단계가 생기면서 nullable 이 됐다.
    private LocalDateTime paidAt;

    private LocalDateTime canceledAt;

    //만료돼야 하는 '예정' 시각.
    @Column(name = "reservation_expires_at", nullable = false)
    private LocalDateTime reservationExpiresAt;

    /**
     * 실제로 만료 처리된 시각.
     *
     * reservationExpiresAt 과 나누는 이유는 배치 주기 때문에 예정과 실제가 어긋나서다.
     * 동시에 두 값을 비교하면 상태를 늘리지 않고도 이탈 종류를 구분할 수 있다.
     * expiredAt < reservationExpiresAt 이면 사용자가 직접 나간 것이고, 그 반대면 방치다.
     */
    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    /**
     * 가장 최근 결제를 시도한 시각. 외부 결제 호출 <b>전에</b> 별도 트랜잭션으로 커밋한다.
     *
     * 이 값이 없으면 타임아웃 시 order 트랜잭션이 롤백되면서 "결제를 시도했다는 사실"까지
     * 사라진다. 만료 배치는 이 값으로 처리를 가른다 — null 이면 그냥 만료시키고,
     * 값이 있으면 현재 시도 번호로 만든 멱등키의 차감 결과를 조회해 확정한다.
     */
    @Column(name = "pay_attempted_at")
    private LocalDateTime payAttemptedAt;

    /**
     * 현재 결제 시도 번호. 정상 FAIL 응답을 받아도 즉시 올리지 않고,
     * 사용자가 다음 결제를 시작할 때만 증가한다.
     */
    @Column(name = "pay_attempt_no", nullable = false)
    private int payAttemptNo;

    /** 다음 사용자 결제를 새 멱등키로 시작해야 하는지 나타내는 전이 표식. */
    @Column(name = "advance_pay_attempt_on_next_request", nullable = false)
    private boolean advancePayAttemptOnNextRequest;

    /**
     * 정적 팩토리. <b>PENDING 으로 시작한다</b>(결제 전).
     * 진행 중 주문 슬롯도 여기서 잡는다.
     */
    public static Order createPending(
            Long memberId,
            String buyerNameSnapshot,
            SalesType salesType,
            BigDecimal totalAmount,
            LocalDateTime reservationExpiresAt) {
        Order order = new Order();
        order.memberId = memberId;
        order.buyerNameSnapshot = buyerNameSnapshot;
        order.salesType = salesType;
        order.totalAmount = totalAmount;
        order.orderState = OrderState.PENDING;
        order.activeMemberId = memberId;
        order.createdAt = LocalDateTime.now();
        order.reservationExpiresAt = reservationExpiresAt;
        order.payAttemptNo = 1;
        order.advancePayAttemptOnNextRequest = false;
        return order;
    }

    public void addItem(OrderItem item) {
        this.items.add(item);
        item.setOrder(this);
    }

    // ── 상태 전이 ────────────────────────────────────────────────
    // 종료 전이는 모두 releaseActiveSlot() 을 지난다. 서비스 쪽에서 슬롯을 지우면
    // 전이가 하나 늘 때마다 빠뜨릴 수 있고, 한 번만 빠뜨려도 그 회원이 영구히 잠긴다.

    /**
     * 외부 결제 호출 직전. 이번 요청에 사용할 시도 번호를 확정하고 시도 사실을 기록한다.
     * 정상 FAIL 응답 뒤의 다음 사용자 요청에서만 번호를 올린다.
     */
    public int preparePayAttempt() {
        requirePending();
        if (advancePayAttemptOnNextRequest) {
            this.payAttemptNo = Math.incrementExact(this.payAttemptNo);
            this.advancePayAttemptOnNextRequest = false;
        }
        this.payAttemptedAt = LocalDateTime.now();
        return this.payAttemptNo;
    }

    /**
     * 정상 pay 응답으로 FAIL을 받은 경우 다음 사용자 요청을 새 시도로 표시한다.
     * 종료된 주문이나 더 오래된 시도의 늦은 응답은 현재 시도를 오염시키지 않고 무시한다.
     */
    public boolean markPayFailed(int responseAttemptNo) {
        if (this.orderState != OrderState.PENDING || this.payAttemptNo != responseAttemptNo) {
            return false;
        }
        this.advancePayAttemptOnNextRequest = true;
        return true;
    }

    public String currentPaymentIdempotencyKey() {
        if (this.orderId == null) {
            throw new IllegalStateException("저장되지 않은 주문은 결제 멱등키를 만들 수 없습니다.");
        }
        return "order-%d-%02d".formatted(this.orderId, this.payAttemptNo);
    }

    public void markPaid() {
        requirePending();
        this.orderState = OrderState.PAID;
        this.paidAt = LocalDateTime.now();
        releaseActiveSlot();
    }

    public void markFailed(OrderFailReason reason) {
        requirePending();
        this.orderState = OrderState.FAILED;
        this.failReason = reason;
        releaseActiveSlot();
    }

    //방치·이탈로 결제 전에 끝난 주문. 환불이 없고 주문 내역에도 뜨지 않는다.
    public void markExpired() {
        requirePending();
        this.orderState = OrderState.EXPIRED;
        this.expiredAt = LocalDateTime.now();
        releaseActiveSlot();
    }

    //결제 후 취소 — 환불 + 재고 복구가 따라온다. 주문 내역에 남는다.
    public void cancel() {
        validateCancelable();
        items.forEach(OrderItem::cancel);
        this.orderState = OrderState.CANCELED;
        this.canceledAt = LocalDateTime.now();
        releaseActiveSlot();
    }

    /**
     * 취소 가능 여부만 본다. 상태는 바꾸지 않는다.
     *
     * <b>환불·재고 복구보다 먼저</b> 부르기 위한 메서드다. 검증이 그 뒤로 밀리면
     * 돈만 돌려주고 취소는 실패하는 상황이 생기는데, 환불은 외부 서비스라 롤백되지 않는다.
     * {@link #cancel()} 도 내부에서 이 검사를 다시 하므로 검증이 빠질 일은 없다.
     */
    public void validateCancelable() {
        if (this.orderState != OrderState.PAID) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE);
        }
        //항목 하나라도 확정되면 정산이 이미 나갔으므로 되돌릴 수 없다.
        if (hasAnyConfirmedItem()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELABLE);
        }
    }

    /**
     * 항목 하나를 확정한다. Order 상태는 바꾸지 않는다.
     *
     * 반환값은 모든 항목의 확정이 끝났는지이며, 주문 상태 전이가 아니라 Payment를
     * 주문당 한 번 최종화할 시점을 판단하는 내부 용도로만 사용한다.
     */
    public boolean confirmItem(OrderItem item) {
        if (this.orderState != OrderState.PAID) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CONFIRMABLE);
        }
        item.confirm();
        return isAllItemsConfirmed();
    }

    public boolean isAllItemsConfirmed() {
        return items.stream().allMatch(OrderItem::isConfirmed);
    }

    public boolean hasAnyConfirmedItem() {
        return items.stream().anyMatch(OrderItem::isConfirmed);
    }

    /**
     * 누수된 슬롯 반납. <b>정상 경로가 아니라 자가 치유용이다.</b>
     *
     * 종료 상태인데 슬롯이 남아 있다는 것은 전이 어딘가에서 반납을 빠뜨렸다는 뜻이다.
     * 그 회원은 그대로 두면 영구히 주문을 못 하므로 배치가 풀어 준다.
     * PENDING 인 주문에는 쓰지 않는다 — 아직 진행 중이라 슬롯이 있는 게 맞다.
     */
    public void releaseLeakedSlot() {
        if (!this.orderState.isTerminal()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE);
        }
        releaseActiveSlot();
    }

    public boolean isReservationExpired(LocalDateTime now) {
        return !now.isBefore(reservationExpiresAt);
    }

    public boolean isDrop() {
        return this.salesType == SalesType.DROP;
    }

    //판매자 본인 항목만. 남의 항목이 판매자 화면에 보이면 안 된다.
    public List<OrderItem> itemsOf(Long sellerId) {
        return items.stream()
                .filter(item -> item.getSellerId().equals(sellerId))
                .toList();
    }

    private void requirePending() {
        if (this.orderState != OrderState.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE);
        }
    }

    /**
     * 진행 중 주문 슬롯 반납.
     *
     * 만료 배치에 "PENDING 이 아닌데 슬롯이 남아 있는 행"을 청소하는 쿼리가 따로 있지만,
     * 그건 누수가 났을 때의 자가 치유이지 정상 경로가 아니다.
     */
    private void releaseActiveSlot() {
        this.activeMemberId = null;
    }
}
