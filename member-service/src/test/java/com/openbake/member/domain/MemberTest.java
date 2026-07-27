package com.openbake.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @Test
    @DisplayName("name과 phoneNumber를 모두 주면 둘 다 변경한다")
    void updateMember_bothFields_updatesBoth() {
        Member member = Member.create("홍길동", "010-1234-5678");

        member.updateMember("김철수", "010-9999-8888");

        assertThat(member.getName()).isEqualTo("김철수");
        assertThat(member.getPhoneNumber()).isEqualTo("010-9999-8888");
    }

    @Test
    @DisplayName("name만 주면 phoneNumber는 기존 값을 유지한다")
    void updateMember_nameOnly_keepsExistingPhoneNumber() {
        Member member = Member.create("홍길동", "010-1234-5678");

        member.updateMember("김철수", null);

        assertThat(member.getName()).isEqualTo("김철수");
        assertThat(member.getPhoneNumber()).isEqualTo("010-1234-5678");
    }

    @Test
    @DisplayName("phoneNumber만 주면 name은 기존 값을 유지한다")
    void updateMember_phoneNumberOnly_keepsExistingName() {
        Member member = Member.create("홍길동", "010-1234-5678");

        member.updateMember(null, "010-9999-8888");

        assertThat(member.getName()).isEqualTo("홍길동");
        assertThat(member.getPhoneNumber()).isEqualTo("010-9999-8888");
    }

    @Test
    @DisplayName("둘 다 null이면 아무것도 바뀌지 않는다")
    void updateMember_bothNull_keepsExistingValues() {
        Member member = Member.create("홍길동", "010-1234-5678");

        member.updateMember(null, null);

        assertThat(member.getName()).isEqualTo("홍길동");
        assertThat(member.getPhoneNumber()).isEqualTo("010-1234-5678");
    }
}
