package com.openbake.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public Member(String name, String phoneNumber) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.role = Role.CUSTOMER;
        this.status = MemberStatus.ACTIVE;
    }

    public static Member create(String name, String phoneNumber) {
        return new Member(name, phoneNumber);
    }

    public static Member createFromGoogle(String name) {
        return new Member(name, null);
    }

    public void suspend() {
        this.status = MemberStatus.SUSPENDED;
    }

    public void withdraw() {
        this.name = "탈퇴한 회원";
        this.phoneNumber = null;
        this.status = MemberStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
    }

    public void updateMember(String name, String phoneNumber) {
        if (name != null) {
            this.name = name;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
    }

}
