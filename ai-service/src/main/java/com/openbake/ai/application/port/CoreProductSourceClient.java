package com.openbake.ai.application.port;

import com.openbake.ai.application.CoreProductSource;
import java.util.List;

public interface CoreProductSourceClient {

    ProductSourcePage fetchPage(int page, int size);

    record ProductSourcePage(
            List<CoreProductSource> content,
            int number,
            int totalPages,
            long totalElements,
            boolean last) {
    }
}
