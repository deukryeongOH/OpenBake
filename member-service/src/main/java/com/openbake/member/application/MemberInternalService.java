package com.openbake.member.application;

import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.member.application.dto.internal.MemberInternalResult;
import com.openbake.member.domain.Member;
import com.openbake.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberInternalService {

    private final MemberRepository memberRepository;

    public MemberInternalResult getMemberName(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("대상을 찾을 수 없습니다."));

        return new MemberInternalResult(member.getName(), member.getPhoneNumber());
    }
}
