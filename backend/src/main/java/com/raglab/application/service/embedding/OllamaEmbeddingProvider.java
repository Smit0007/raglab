package com.raglab.application.service.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglab.application.service.RagRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Component
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingProvider.class);
    private static final int REQUIRED_DIMENSION = 768;

    private final String ollamaBaseUrl;
    private final String embeddingModel;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaEmbeddingProvider(
        @Value("${raglab.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
        @Value("${raglab.ollama.embedding-model:nomic-embed-text}") String embeddingModel,
        ObjectMapper objectMapper
    ) {
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    }

    @Override
    public int getDimension() {
        return REQUIRED_DIMENSION;
    }

    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Cannot generate embedding for empty text");
        }

        try {
            // Attempt Ollama HTTP API /api/embeddings
            Map<String, Object> reqMap = Map.of(
                "model", embeddingModel,
                "prompt", text
            );
            String jsonBody = objectMapper.writeValueAsString(reqMap);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + "/api/embeddings"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode embeddingNode = root.get("embedding");
                if (embeddingNode != null && embeddingNode.isArray()) {
                    float[] vec = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        vec[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    if (vec.length == REQUIRED_DIMENSION) {
                        return vec;
                    }
                    log.warn("Ollama returned vector dimension {} but schema requires {}", vec.length, REQUIRED_DIMENSION);
                }
            }
        } catch (Exception e) {
            log.debug("Ollama embedding endpoint unavailable ({}). Utilizing baseline 768-dim deterministic projection.", e.getMessage());
        }

        // Deterministic baseline embedding projection matching schema
        return RagRetrievalService.generatePseudoEmbedding(text, REQUIRED_DIMENSION);
    }

    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>(texts.size());
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        return embeddings;
    }
}
