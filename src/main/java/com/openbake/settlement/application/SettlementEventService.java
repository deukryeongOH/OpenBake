package com.openbake.settlement.application;

import com.openbake.settlement.domain.SettlementTarget;
import com.openbake.settlement.domain.SettlementTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 구매확정 이벤트를 처리하는 애플리케이션 서비스입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SettlementEventService {

    private static final String PURCHASE_CONFIRMED_EVENT =
            "PURCHASE_CONFIRMED";

    /**
     * MVP에서는 서비스 수수료율을 10%로 고정합니다.
     *
     * 추후 수수료 정책 테이블이 도입되면
     * 판매자 또는 드롭별 수수료 정책을 조회하도록 변경합니다.
     */
    private static final BigDecimal DEFAULT_COMMISSION_RATE =
            new BigDecimal("0.1000");

    private final SettlementInboxEventRepository settlementInboxRepository;
    private final SettlementTargetRepository settlementTargetRepository;

    /**
     * 구매확정 이벤트를 받아 정산 대상을 생성합니다.
     *
     * 동일한 eventId 또는 동일한 주문 상품이 이미 처리됐다면
     * 새로운 정산 대상을 만들지 않고 중복 처리 결과를 반환합니다.
     */
    public SettlementEventResult receive(
            ReceivePurchaseConfirmedCommand command
    ) {
        validateCommand(command);

        /*
         * 동일 이벤트가 이미 처리된 경우입니다.
         *
         * 주문 항목을 기준으로 기존 SettlementTarget을 찾아
         * 중복 처리 결과를 반환합니다.
         */
        if (settlementInboxRepository.existsByEventId(command.eventId())) {
            Long existingTargetId = settlementTargetRepository
                    .findByOrderIdAndOrderItemId(
                            command.orderId(),
                            command.orderItemId()
                    )
                    .map(SettlementTarget::getId)
                    .orElse(null);

            return SettlementEventResult.duplicated(
                    command.eventId(),
                    existingTargetId
            );
        }

        /*
         * eventId는 다르지만 같은 주문 항목인 경우입니다.
         *
         * 주문 서비스에서 새로운 eventId로 이벤트를 다시 만들어도
         * 하나의 주문 항목은 한 번만 정산 대상이 되어야 합니다.
         */
        var existingTarget = settlementTargetRepository
                .findByOrderIdAndOrderItemId(
                        command.orderId(),
                        command.orderItemId()
                );

        if (existingTarget.isPresent()) {
            settlementInboxRepository.save(
                    command.eventId(),
                    PURCHASE_CONFIRMED_EVENT
            );

            return SettlementEventResult.duplicated(
                    command.eventId(),
                    existingTarget.get().getId()
            );
        }
        /*
         * 새로운 정산 대상을 생성합니다.
         */
        SettlementTarget settlementTarget = SettlementTarget.create(
                command.eventId(),
                command.orderId(),
                command.orderItemId(),
                command.sellerId(),
                command.productId(),
                command.productNameSnapshot(),
                command.quantity(),
                command.grossAmount(),
                DEFAULT_COMMISSION_RATE,
                command.purchaseConfirmedAt()
        );

        SettlementTarget savedTarget =
                settlementTargetRepository.save(settlementTarget);

        /*
         * SettlementTarget과 Inbox는 같은 트랜잭션 안에서 저장됩니다.
         *
         * 둘 중 하나라도 저장에 실패하면 전체 작업이 롤백됩니다.
         */
        settlementInboxRepository.save(
                command.eventId(),
                PURCHASE_CONFIRMED_EVENT
        );

        return SettlementEventResult.created(
                command.eventId(),
                savedTarget.getId()
        );
    }

    private void validateCommand(
            ReceivePurchaseConfirmedCommand command
    ) {
        Objects.requireNonNull(command, "command는 필수입니다.");

        if (command.eventId() == null
                || command.eventId().isBlank()) {
            throw new IllegalArgumentException(
                    "eventId는 필수입니다."
            );
        }
    }
}