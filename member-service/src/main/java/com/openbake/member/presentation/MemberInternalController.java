package com.openbake.member.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.member.application.MemberInternalService;
import com.openbake.member.presentation.dto.internal.MemberInternalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/members")
@RequiredArgsConstructor
public class MemberInternalController {

    private final MemberInternalService memberInternalService;

    @GetMapping("/{memberId}")
    public ApiResponse<MemberInternalResponse> getOrderSnapshot(@PathVariable Long memberId) {
        return ApiResponse.ok(MemberInternalResponse.from(memberInternalService.getMemberName(memberId)));
    }
}
