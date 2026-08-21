package com.openbake.ai.application;

public class RecommendationUnavailableException extends RuntimeException {

    public RecommendationUnavailableException(Throwable cause) {
        super("추천 서비스를 일시적으로 사용할 수 없습니다.", cause);
    }
}
