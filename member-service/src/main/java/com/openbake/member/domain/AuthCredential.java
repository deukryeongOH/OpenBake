package com.openbake.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "auths")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "provider_id")
    private String providerId;

    public static AuthCredential createLocal(
            Long memberId, String email, String passwordHash) {
        AuthCredential auth = new AuthCredential();
        auth.memberId = memberId;
        auth.provider = AuthProvider.LOCAL;
        auth.email = email;
        auth.passwordHash = passwordHash;
        return auth;
    }

    public static AuthCredential createGoogle(
            Long memberId, AuthProvider provider, String providerId, String email) {
        AuthCredential auth = new AuthCredential();
        auth.memberId = memberId;
        auth.provider = provider;
        auth.providerId = providerId;
        auth.email = email;
        return auth;
    }

    public void changePassword(String newPassword) {
        this.passwordHash = newPassword;
    }

    public void withdraw() {
        this.email = "withdrawn-" + this.memberId + "@deleted.local";
        this.passwordHash = null;
    }

}
