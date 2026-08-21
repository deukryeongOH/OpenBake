package com.openbake.ai.application;

public class SemanticSearchUnavailableException extends RuntimeException {

    public SemanticSearchUnavailableException(Throwable cause) {
        super("의미 검색을 일시적으로 사용할 수 없습니다.", cause);
    }
}
