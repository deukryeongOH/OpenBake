package com.openbake.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentMemberProviderTest {

    private final CurrentMemberProvider provider = new CurrentMemberProvider();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void optionalIdIsEmptyWithoutLoginAndPresentForGatewayIdentity() {
        assertThat(provider.findId()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        7L, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

        assertThat(provider.findId()).contains(7L);
    }

    @Test
    void optionalIdDoesNotCastAnonymousPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(provider.findId()).isEmpty();
    }
}
