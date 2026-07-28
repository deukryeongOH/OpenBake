package com.openbake.drop.presentation;


import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.drop.application.DropLockFacade;
import com.openbake.drop.presentation.dto.DropReserveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/drops")
@RequiredArgsConstructor
public class DropLockController {

    private final DropLockFacade dropLockFacade;
    private final CurrentMemberProvider provider;

    @PostMapping("/{dropId}/lock-start")
    public ApiResponse<String> reserveStock(@PathVariable("dropId") Long dropId, @RequestBody DropReserveRequest request){
        Long memberId = provider.getId();
        dropLockFacade.reserveStock(dropId, memberId, request.getQuantity());

        return ApiResponse.ok("재고 선점 및 장바구니 담기 완료");
    }
}
