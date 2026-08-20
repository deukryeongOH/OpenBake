package com.openbake.ai.application.port;

import com.openbake.ai.application.CoreProductCard;
import java.util.List;

public interface CoreRecommendationClient {

    List<CoreProductCard> validate(Long memberId, List<Long> productIds);

    List<CoreProductCard> latest(Long memberId, int size);
}
