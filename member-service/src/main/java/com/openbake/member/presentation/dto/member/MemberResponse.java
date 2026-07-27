package com.openbake.member.presentation.dto.member;

import com.openbake.member.domain.Member;
import com.openbake.member.domain.MemberStatus;
import com.openbake.member.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberResponse (
    @Schema(description = "회원 ID", example = "1")
    Long id,
    @Schema(description = "이름", example = "이세종")
    String name,
    @Schema(description = "이메일 (AuthCredential에서 조합)", example = "sejong@example.com")
    String email,
    @Schema(description = "휴대폰 번호", example = "010-1234-5678")
    String phoneNumber,
    @Schema(description = "권한", example = "CUSTOMER")
    Role role,
    @Schema(description = "회원 상태", example = "ACTIVE")
    MemberStatus status
) {
    public MemberResponse(Member member, String email) {
        this(member.getId(), member.getName(), email, member.getPhoneNumber(),
                member.getRole(), member.getStatus());
    }
}
