package com.openbake.gateway.auth.jwt;

public enum JwtVerificationError {
    EXPIRED,                // 만료
    INVALID_SIGNATURE,      // 서명 불일치
    MALFORMED,              // Jwt 형식 오류
    INVALID_CLAIMS,         // sub/role 계약 오류
    UNSUPPORTED;            // 지원하지 않는 JWT

    public String safeCode() {
        return switch (this) {
            case EXPIRED -> "TOKEN_EXPIRED";
            case INVALID_CLAIMS -> "TOKEN_CLAIMS_INVALID";
            case INVALID_SIGNATURE, MALFORMED, UNSUPPORTED ->
                    "TOKEN_INVALID";
        };
    }
}
