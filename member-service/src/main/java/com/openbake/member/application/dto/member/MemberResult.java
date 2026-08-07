package com.openbake.member.application.dto.member;

import com.openbake.member.domain.Member;
import com.openbake.member.domain.MemberStatus;
import com.openbake.member.domain.Role;

public record MemberResult(
        Long id,
        String name,
        String email,
        String phoneNumber,
        Role role,
        MemberStatus status
) {

    public static MemberResult from(Member member, String email) {
        return new MemberResult(
                member.getId(),
                member.getName(),
                email,
                member.getPhoneNumber(),
                member.getRole(),
                member.getStatus()
        );
    }
}
