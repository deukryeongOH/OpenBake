package com.openbake.member.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.member.application.MemberService;
import com.openbake.member.presentation.dto.member.MemberResponse;
import com.openbake.member.presentation.dto.member.MemberUpdateRequest;
import com.openbake.member.presentation.dto.member.MemberUpdateResponse;
import com.openbake.member.presentation.dto.member.PasswordChangeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/{id}")
    public ApiResponse<MemberResponse> getMember(@PathVariable Long id) {
        return ApiResponse.ok(memberService.getMemberById(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<MemberUpdateResponse> updateMember(
            @PathVariable Long id, @Valid @RequestBody MemberUpdateRequest request) {
        return ApiResponse.ok(memberService.updateMember(id, request));
    }

    @PatchMapping("/{id}/password")
    public ApiResponse<Void> changePassword(
            @PathVariable Long id, @Valid @RequestBody PasswordChangeRequest request) {
        memberService.changePassword(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> withdrawMember(@PathVariable Long id) {
        memberService.withdrawMember(id);
        return ApiResponse.ok();
    }

}
