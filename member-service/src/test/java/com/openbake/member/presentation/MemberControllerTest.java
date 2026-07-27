package com.openbake.member.presentation;

import com.openbake.common.exception.AccessDeniedException;
import com.openbake.common.exception.AuthenticationFailedException;
import com.openbake.common.exception.EntityNotFoundException;
import com.openbake.member.application.MemberService;
import com.openbake.member.domain.AccessTokenRepository;
import com.openbake.member.domain.MemberStatus;
import com.openbake.member.domain.Role;
import com.openbake.member.infrastructure.jwt.JwtTokenProvider;
import com.openbake.member.presentation.dto.member.MemberResponse;
import com.openbake.member.presentation.dto.member.MemberUpdateRequest;
import com.openbake.member.presentation.dto.member.MemberUpdateResponse;
import com.openbake.member.presentation.dto.member.PasswordChangeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AccessTokenRepository accessTokenRepository;

    @Test
    @DisplayName("회원 조회 성공 시 200과 회원 정보를 반환한다")
    void getMember_success() throws Exception {
        given(memberService.getMemberById(1L)).willReturn(
                new MemberResponse(1L, "홍길동", "test@example.com", "010-1234-5678", Role.CUSTOMER, MemberStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/members/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("본인/admin이 아니면 403과 에러 응답을 반환한다")
    void getMember_accessDenied() throws Exception {
        given(memberService.getMemberById(1L)).willThrow(new AccessDeniedException());

        mockMvc.perform(get("/api/v1/members/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ME004"));
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 404와 에러 응답을 반환한다")
    void getMember_notFound() throws Exception {
        given(memberService.getMemberById(1L)).willThrow(new EntityNotFoundException("대상을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/v1/members/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C003"));
    }

    @Test
    @DisplayName("회원정보 수정 성공 시 200과 변경된 정보를 반환한다")
    void updateMember_success() throws Exception {
        MemberUpdateRequest request = new MemberUpdateRequest("김철수", "010-9999-8888");
        given(memberService.updateMember(eq(1L), any()))
                .willReturn(new MemberUpdateResponse(1L, "김철수", "010-9999-8888"));

        mockMvc.perform(patch("/api/v1/members/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("김철수"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010-9999-8888"));
    }

    @Test
    @DisplayName("빈 문자열로 수정 요청하면 400과 에러 응답을 반환한다")
    void updateMember_blankName() throws Exception {
        MemberUpdateRequest request = new MemberUpdateRequest("", "010-9999-8888");

        mockMvc.perform(patch("/api/v1/members/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("본인이 아니면 403과 에러 응답을 반환한다")
    void updateMember_accessDenied() throws Exception {
        MemberUpdateRequest request = new MemberUpdateRequest("김철수", "010-9999-8888");
        given(memberService.updateMember(eq(1L), any())).willThrow(new AccessDeniedException());

        mockMvc.perform(patch("/api/v1/members/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ME004"));
    }

    @Test
    @DisplayName("비밀번호 변경 성공 시 200을 반환한다")
    void changePassword_success() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest("oldPassword123", "newPassword456");

        mockMvc.perform(patch("/api/v1/members/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("길이가 짧은 비밀번호로 요청하면 400과 에러 응답을 반환한다")
    void changePassword_tooShortNewPassword() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest("oldPassword123", "short");

        mockMvc.perform(patch("/api/v1/members/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("본인이 아니거나 GOOGLE 전용 계정이면 403과 에러 응답을 반환한다")
    void changePassword_accessDenied() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest("oldPassword123", "newPassword456");
        willThrow(new AccessDeniedException()).given(memberService).changePassword(eq(1L), any());

        mockMvc.perform(patch("/api/v1/members/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ME004"));
    }

    @Test
    @DisplayName("현재 비밀번호가 일치하지 않으면 401과 에러 응답을 반환한다")
    void changePassword_wrongCurrentPassword() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest("wrongPassword", "newPassword456");
        willThrow(new AuthenticationFailedException("비밀번호가 일치하지 않습니다."))
                .given(memberService).changePassword(eq(1L), any());

        mockMvc.perform(patch("/api/v1/members/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ME003"));
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 404와 에러 응답을 반환한다")
    void changePassword_notFound() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest("oldPassword123", "newPassword456");
        willThrow(new EntityNotFoundException("대상을 찾을 수 없습니다."))
                .given(memberService).changePassword(eq(1L), any());

        mockMvc.perform(patch("/api/v1/members/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C003"));
    }

    @Test
    @DisplayName("탈퇴 성공 시 200을 반환한다")
    void withdrawMember_success() throws Exception {
        mockMvc.perform(delete("/api/v1/members/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("본인이 아니면 403과 에러 응답을 반환한다")
    void withdrawMember_accessDenied() throws Exception {
        willThrow(new AccessDeniedException()).given(memberService).withdrawMember(1L);

        mockMvc.perform(delete("/api/v1/members/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ME004"));
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 404와 에러 응답을 반환한다")
    void withdrawMember_notFound() throws Exception {
        willThrow(new EntityNotFoundException("대상을 찾을 수 없습니다.")).given(memberService).withdrawMember(1L);

        mockMvc.perform(delete("/api/v1/members/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C003"));
    }

    @Test
    @DisplayName("이미 탈퇴한 회원이면 409와 에러 응답을 반환한다")
    void withdrawMember_alreadyWithdrawn() throws Exception {
        willThrow(new IllegalStateException("이미 탈퇴 처리된 회원입니다.")).given(memberService).withdrawMember(1L);

        mockMvc.perform(delete("/api/v1/members/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C002"));
    }
}
