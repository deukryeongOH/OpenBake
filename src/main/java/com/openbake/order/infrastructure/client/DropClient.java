package com.openbake.order.infrastructure.client;

import com.openbake.order.application.port.dto.DropInfo;
import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.domain.Drop;
import com.openbake.drop.domain.DropRepository;
import com.openbake.order.application.port.DropPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

//cart 에도 같은 이름의 어댑터가 있어 빈 이름을 명시한다(클래스 단순명이 겹치면 기동 실패).
@Component("orderDropClient")
@RequiredArgsConstructor
public class DropClient implements DropPort {
    private final DropRepository dropRepository;

    @Override
    public DropInfo getDrop(Long dropId) {
        Drop drop = dropRepository.findById(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));

        return new DropInfo(
                drop.getId(),
                drop.getSellerId(),
                drop.getDropProduct().getName(),
                drop.getDropProduct().getPrice()
        );
    }
}
