package com.raglab.application.service.embedding;

import java.util.List;

public interface EmbeddingProvider {
    int getDimension();
    float[] generateEmbedding(String text);
    List<float[]> generateEmbeddings(List<String> texts);
}
