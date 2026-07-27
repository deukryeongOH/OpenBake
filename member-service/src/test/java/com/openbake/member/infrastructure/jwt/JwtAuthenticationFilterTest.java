package com.openbake.member.infrastructure.jwt;

import com.openbake.member.domain.AccessTokenRepository;
import com.openbake.member.domain.Role;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이 있으면 SecurityContext에 memberId와 role 권한을 채운다")
    void doFilterInternal_validToken_setsAuthentication() throws Exception {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, accessTokenRepository);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        given(jwtTokenProvider.isValid("valid-access-token")).willReturn(true);
        given(accessTokenRepository.isBlacklisted("valid-access-token")).willReturn(false);
        given(jwtTokenProvider.getMemberId("valid-access-token")).willReturn(1L);
        given(jwtTokenProvider.getRole("valid-access-token")).willReturn(Role.CUSTOMER);

        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_CUSTOMER");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("서명은 유효해도 블랙리스트에 있으면 인증을 설정하지 않고 다음 필터로 넘긴다")
    void doFilterInternal_blacklistedToken_doesNotAuthenticate() throws Exception {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, accessTokenRepository);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer withdrawn-member-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        given(jwtTokenProvider.isValid("withdrawn-member-token")).willReturn(true);
        given(accessTokenRepository.isBlacklisted("withdrawn-member-token")).willReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증을 설정하지 않고 다음 필터로 넘긴다")
    void doFilterInternal_noHeader_doesNotAuthenticate() throws Exception {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, accessTokenRepository);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("토큰이 유효하지 않으면 인증을 설정하지 않고 다음 필터로 넘긴다")
    void doFilterInternal_invalidToken_doesNotAuthenticate() throws Exception {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, accessTokenRepository);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        given(jwtTokenProvider.isValid("expired-token")).willReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
