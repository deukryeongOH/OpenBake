package com.openbake.drop.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.drop.application.DropEnterService;
import com.openbake.drop.application.dto.ConfirmEntryResponse;
import com.openbake.drop.application.dto.QueueRankResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/drops")
@RequiredArgsConstructor
public class DropEnterController {

    private final DropEnterService dropEnterService;
    private final CurrentMemberProvider provider;

    @PostMapping("/{dropId}/confirm-entry")
    public ApiResponse<ConfirmEntryResponse> enterDrop(@PathVariable("dropId") Long dropId){
        Long memberId = provider.getId();
        ConfirmEntryResponse response = dropEnterService.confirmEntry(dropId, memberId);
        return ApiResponse.ok(response);
    }


    @PostMapping("/{dropId}/enter") // 대기열 진입 할 때
    public ApiResponse<QueueRankResponse> enterQueue(@PathVariable("dropId") Long dropId){
        Long memberId = provider.getId();
        QueueRankResponse response = dropEnterService.enterQueue(dropId, memberId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{dropId}/queue/rank") // 내 대기열 순번 확인 (프론트에서 지속적으로 호출)
    public ApiResponse<QueueRankResponse> getQueueRank(@PathVariable("dropId") Long dropId){
        Long memberId = provider.getId();
        QueueRankResponse response = dropEnterService.getRank(dropId, memberId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/today/drop") // 오늘 진행하는 드롭ID 가져오기 (대기열 선행 작업, ID가 있어야 대기열 생성가능)
    public ApiResponse<?> getDropId(){
        Long dropId = dropEnterService.getTodayDropId();
        return ApiResponse.ok(dropId);
    }
}
