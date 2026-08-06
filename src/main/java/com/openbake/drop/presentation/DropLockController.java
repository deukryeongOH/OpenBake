package com.openbake.drop.presentation;


import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.drop.application.DropLockFacade;
import com.openbake.drop.application.dto.DropReserveCommand;
import com.openbake.drop.presentation.dto.DropReserveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Drop Lock", description = "드롭 입장 이후 재고 선점(락) 처리")
@RestController
@RequestMapping("/api/v1/drops")
@RequiredArgsConstructor
public class DropLockController {

    private final DropLockFacade dropLockFacade;
    private final CurrentMemberProvider provider;

    @Operation(
            summary = "드롭 재고 선점",
            description = "입장이 확정된 사용자가 원하는 수량만큼 재고를 선점합니다. 동시 요청 폭주에 대비해 드롭 단위로 3초간 락을 획득한 뒤 재고를 차감하며, 재고가 모두 소진되면 드롭 상태를 완료로 변경합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. / DR007 준비된 재고가 모두 소진되었습니다. / DR013 1인당 제한 수량보다 많이 선택했습니다. / DR014 재고를 선점할 수 있는 상태가 아닙니다."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DR001 존재하지 않는 드롭입니다. / DR011 드롭에 참여한 기록이 없습니다. 드롭에 참여한 후 다시 시도해주세요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "DR012 락을 획득하는 과정에서 시스템 오류가 발생했습니다.")
    })
    @PostMapping("/{dropId}/lock-start")
    public ApiResponse<String> reserveStock(
            @Parameter(description = "드롭 ID", example = "1") @PathVariable("dropId") Long dropId,
            @Valid @RequestBody DropReserveRequest request){
        Long memberId = provider.getId();
        DropReserveCommand command = DropReserveCommand.create(request.quantity());
        dropLockFacade.reserveStock(dropId, memberId, command);

        return ApiResponse.ok("재고 선점 및 장바구니 담기 완료");
    }
}
