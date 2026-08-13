package com.openbake.cart.infrastructure.client;

import com.openbake.cart.application.port.ReservationPort;
import com.openbake.cart.application.port.dto.ReservationInfo;
import com.openbake.drop.application.service.DropLockService;
import com.openbake.drop.domain.repository.DropEntryRepository;
import com.openbake.drop.domain.EntryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * ReservationPort 구현체. drop 이 아직 같은 코어 안에 있어 저장소·서비스를 직접 호출한다.
 * drop 의 타입(DropEntry, EntryStatus)은 이 파일 밖으로 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ReservationClient implements ReservationPort {

    private final DropEntryRepository dropEntryRepository;
    private final DropLockService dropLockService;

    /**
     * 응모 한 건에서 선점 여부와 선점 수량을 함께 읽는다.
     *
     * DropLockService.getSelectQuantity 도 수량을 주지만 선점 여부를 알 수 없고,
     * 응모가 없을 때 NEVER_ENTERED 를 던져 cart 의 CART_STOCK_NOT_RESERVED 와 어긋난다.
     * 그래서 조회는 저장소 한 번으로 끝내고 판정은 cart 가 한다.
     */
    @Override
    public Optional<ReservationInfo> findReservation(Long dropId, Long memberId) {
        return dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .map(entry -> new ReservationInfo(
                        entry.getEntryStatus() == EntryStatus.RESERVED,
                        entry.getSelectQuantity()
                ));
    }

    @Override
    public void rollbackStock(Long dropId, Long memberId) {
        dropLockService.rollbackStock(dropId, memberId);
    }
}
