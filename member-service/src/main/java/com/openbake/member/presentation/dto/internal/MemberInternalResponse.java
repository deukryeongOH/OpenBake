package com.openbake.member.presentation.dto.internal;

import com.openbake.member.application.dto.internal.MemberInternalResult;

public record MemberInternalResponse(
        String name,
        String phoneNumber
) {
    public static MemberInternalResponse from(MemberInternalResult result) {
        return new MemberInternalResponse(result.name(), result.phoneNumber());
    }
}