package com.openbake.member.infrastructure;

import com.openbake.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

interface MemberJpaRepository extends JpaRepository<Member, Long> {
}
