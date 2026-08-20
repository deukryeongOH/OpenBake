package com.openbake.drop.application.service;

import com.openbake.drop.application.dto.DropInfoResult;
import com.openbake.interaction.application.ProductViewRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DropDetailService {

    private final DropService dropService;
    private final ProductViewRecorder viewRecorder;

    @Transactional(readOnly = true)
    public DropInfoResult get(Long dropId) {
        DropInfoResult result = dropService.getDropInfo(dropId);
        viewRecorder.record(result.productId(), dropId);
        return result;
    }
}
