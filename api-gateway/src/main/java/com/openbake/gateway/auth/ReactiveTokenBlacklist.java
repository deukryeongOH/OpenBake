package com.openbake.gateway.auth;

import reactor.core.publisher.Mono;

public interface ReactiveTokenBlacklist {

    Mono<Boolean> contains(String rawAccessToken);
}