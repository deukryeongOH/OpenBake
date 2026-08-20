package com.openbake.ai.application.port;

import java.util.List;

public interface EmbeddingClient {
    List<Float> embed(String input);
}
