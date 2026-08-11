package com.openbake.drop.infrastructure.adapter;

import com.openbake.common.security.CurrentMemberProvider;
import com.openbake.drop.infrastructure.port.CurrentMemberPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberAdapter implements CurrentMemberPort {

    private final CurrentMemberProvider currentMemberProvider;

    @Override
    public Long getCurrentMemberId() {
        return currentMemberProvider.getId();
    }
}
