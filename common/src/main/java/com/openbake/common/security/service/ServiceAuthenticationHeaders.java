package com.openbake.common.security.service;

public final class ServiceAuthenticationHeaders {

    public static final String SERVICE_NAME = "X-Openbake-Service-Name";
    public static final String SERVICE_TOKEN = "X-Openbake-Service-Token";
    public static final String AI_SERVICE = "ai-service";
    public static final String CORE_SERVICE = "core-service";

    private ServiceAuthenticationHeaders() {
    }
}
