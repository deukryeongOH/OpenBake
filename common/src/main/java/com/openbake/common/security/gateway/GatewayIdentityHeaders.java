package com.openbake.common.security.gateway;

public final class GatewayIdentityHeaders {

    public static final String MEMBER_ID =
            "X-Openbake-Member-Id";

    public static final String MEMBER_ROLE =
            "X-Openbake-Member-Role";

    public static final String AUTH_SOURCE =
            "X-Openbake-Auth-Source";

    public static final String EXPECTED_AUTH_SOURCE =
            "api-gateway";

    private GatewayIdentityHeaders() {
    }
}