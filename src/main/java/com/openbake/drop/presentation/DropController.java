package com.openbake.drop.presentation;


import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.common.response.ApiResponse;
import com.openbake.drop.application.DropService;
import com.openbake.drop.presentation.dto.DropProductInfoRequest;
import com.openbake.drop.application.dto.DropProductInfoResponse;
import com.openbake.seller.application.CurrentSellerProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/drops")
@RequiredArgsConstructor
public class DropController {

    private final DropService dropService;
    private final CurrentSellerProvider currentSellerProvider;

    @PostMapping("/register") // 등록은 seller 만 되므로 이걸 호출한 사람이 seller인지 확인 필요 -> SpringSecurity @Authentication을 통해 현재 유저의 ID를 받고 그 ID가 seller에 존재하면 접근 허용
    public ApiResponse<DropProductInfoResponse> registerDropProduct(@Valid @RequestBody DropProductInfoRequest dropProductInfoRequest
//                                              ,@AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
//        Long sellerId = customUserDetails.getSellerId();
//        if (sellerId == null) {
//            throw new AccessDeniedException("등록 승인된 판매자 계정만 상품을 등록할 수 있습니다.");
//        } // 검증 로직은 @PreAuthorize 붙이면 삭제 -> Spring Security 들어와야 함.

//        Long sellerId = 1L; // 로컬 테스트용
        Long sellerId = currentSellerProvider.getSellerId().orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE));

        DropProductInfoResponse response = dropService.registerDropProduct(dropProductInfoRequest, sellerId);
        return ApiResponse.ok(response);
    }

}
