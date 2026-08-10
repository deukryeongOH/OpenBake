package com.openbake.member.presentation.dto.member;

import com.openbake.member.application.dto.member.MemberUpdateResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberUpdateResponse(
        @Schema(description = "회원 ID", example = "1")
        Long id,
        @Schema(description = "변경된 이름", example = "이세종")
        String name,
        @Schema(description = "변경된 전화번호", example = "010-9999-8888")
        String phoneNumber
) {
        public static MemberUpdateResponse from(MemberUpdateResult result) {
                return new MemberUpdateResponse(result.id(), result.name(), result.phoneNumber());
        }
}
