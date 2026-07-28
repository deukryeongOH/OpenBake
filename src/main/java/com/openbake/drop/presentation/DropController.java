package com.openbake.drop.presentation;


import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.common.response.ApiResponse;
import com.openbake.drop.application.DropService;
import com.openbake.drop.application.dto.DropProductInfo;
import com.openbake.drop.presentation.dto.DropProductInfoRequest;
import com.openbake.drop.application.dto.DropProductInfoResponse;
import com.openbake.seller.application.CurrentSellerProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/drops")
@RequiredArgsConstructor
public class DropController {

    private final DropService dropService;
    private final CurrentSellerProvider currentSellerProvider;

    @PostMapping("/register") // 등록은 seller 만 되므로 이걸 호출한 사람이 seller인지 확인 필요 -> SpringSecurity @Authentication을 통해 현재 유저의 ID를 받고 그 ID가 seller에 존재하면 접근 허용
    public ApiResponse<DropProductInfoResponse> registerDropProduct(@Valid @RequestBody DropProductInfoRequest dropProductInfoRequest
    ) {
        Long sellerId = currentSellerProvider.getSellerId().orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE));

        DropProductInfoResponse response = dropService.registerDropProduct(dropProductInfoRequest, sellerId);
        return ApiResponse.ok(response);
    }


    @GetMapping("/{dropId}/info")
    public ApiResponse<DropProductInfo> getDropProductInfo(@PathVariable("dropId") Long dropId){
        DropProductInfo info = dropService.getDropProductInfo(dropId);
        return ApiResponse.ok(info);
    }

    // 판매자 본인이 등록한 드롭 목록 조회
    @GetMapping("/mine")
    public ApiResponse<List<DropProductInfoResponse>> getMyDrops() {
        Long sellerId = currentSellerProvider.getSellerId().orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE));

        List<DropProductInfoResponse> response = dropService.getMyDrops(sellerId);
        return ApiResponse.ok(response);
    }

    // 판매자 본인의 드롭 수정 (시작 전인 드롭만 가능)
    @PatchMapping("/{dropId}")
    public ApiResponse<DropProductInfoResponse> updateDropProduct(@PathVariable("dropId") Long dropId,
                                                                    @Valid @RequestBody DropProductInfoRequest dropProductInfoRequest) {
        Long sellerId = currentSellerProvider.getSellerId().orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE));

        DropProductInfoResponse response = dropService.updateDropProduct(dropId, sellerId, dropProductInfoRequest);
        return ApiResponse.ok(response);
    }

    // 판매자 본인의 드롭 삭제 (시작 전인 드롭만 가능)
    @DeleteMapping("/{dropId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDropProduct(@PathVariable("dropId") Long dropId) {
        Long sellerId = currentSellerProvider.getSellerId().orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE));

        dropService.deleteDropProduct(dropId, sellerId);
    }

}
