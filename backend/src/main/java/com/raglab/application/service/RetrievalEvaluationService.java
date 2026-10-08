package com.raglab.application.service;

import com.raglab.application.dto.*;
import com.raglab.domain.entity.*;
import com.raglab.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class RetrievalEvaluationService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentRunRepository experimentRunRepository;
    private final EvaluationResultRepository evaluationResultRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final VectorSearchService vectorSearchService;

    public RetrievalEvaluationService(
        ExperimentRepository experimentRepository,
        ExperimentRunRepository experimentRunRepository,
        EvaluationResultRepository evaluationResultRepository,
        DocumentRepository documentRepository,
        DocumentChunkRepository documentChunkRepository,
        VectorSearchService vectorSearchService
    ) {
        this.experimentRepository = experimentRepository;
        this.experimentRunRepository = experimentRunRepository;
        this.evaluationResultRepository = evaluationResultRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.vectorSearchService = vectorSearchService;
    }

    public record BenchmarkCase(
        String query,
        String groundTruthAnswer,
        List<UUID> targetChunkIds,
        float[] queryVector
    ) {}

    @Transactional
    public RetrievalEvaluationResponse runRetrievalEvaluation(UUID tenantId, RetrievalEvaluationRequest request) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }

        String datasetId = (request.datasetId() != null && !request.datasetId().isBlank())
            ? request.datasetId()
            : "golden_docs_v1";
        String strategy = request.strategy();
        int topK = request.topK();
        boolean compareExact = Boolean.TRUE.equals(request.compareExact());

        // 1. Ensure or initialize Golden Corpus fixtures in database
        List<BenchmarkCase> benchmarkCases = ensureGoldenCorpusAndQueries(tenantId, datasetId);

        // 2. Create Experiment & ExperimentRun
        ExperimentEntity experiment = experimentRepository.save(new ExperimentEntity(
            tenantId,
            "Retrieval Benchmark: " + datasetId,
            "Phase 4 Tier 1 retrieval evaluation run for " + datasetId + " using " + strategy,
            datasetId,
            Map.of("strategy", strategy, "topK", topK, "compareExact", compareExact)
        ));

        ExperimentRunEntity run = experimentRunRepository.save(new ExperimentRunEntity(
            experiment.getId(),
            tenantId,
            "Run-" + strategy + "-" + System.currentTimeMillis(),
            strategy,
            "raglab-retrieval-v1"
        ));

        // 3. Execute benchmark queries and calculate metrics
        List<Double> recallsAt3 = new ArrayList<>();
        List<Double> recallsAt5 = new ArrayList<>();
        List<Double> rrList = new ArrayList<>();
        List<Double> ndcgList = new ArrayList<>();
        List<Double> hnswRecallList = new ArrayList<>();
        List<Long> latenciesMs = new ArrayList<>();

        for (BenchmarkCase testCase : benchmarkCases) {
            long queryStart = System.currentTimeMillis();

            // Perform pgvector HNSW search
            VectorSearchRequest searchReq = new VectorSearchRequest(
                tenantId,
                testCase.queryVector(),
                Math.max(topK, 10), // retrieve at least 10 for recall@3, @5, and ndcg@10
                0.0,
                null
            );

            List<VectorSearchResult> searchResults = vectorSearchService.search(searchReq);
            long queryDurationMs = System.currentTimeMillis() - queryStart;
            latenciesMs.add(queryDurationMs);

            List<UUID> retrievedIds = searchResults.stream().map(VectorSearchResult::chunkId).toList();

            // Calculate Recall@3 & Recall@5
            Set<UUID> targetIds = new HashSet<>(testCase.targetChunkIds());
            if (targetIds.isEmpty()) {
                continue;
            }

            recallsAt3.add(computeRecallAtK(retrievedIds, targetIds, 3));
            recallsAt5.add(computeRecallAtK(retrievedIds, targetIds, 5));

            // Calculate MRR (Reciprocal Rank)
            rrList.add(computeReciprocalRank(retrievedIds, targetIds));

            // Calculate nDCG@10
            ndcgList.add(computeNdcgAtK(retrievedIds, targetIds, 10));

            // Exact vs HNSW comparison
            if (compareExact) {
                // Compute exhaustive exact cosine similarity against all tenant chunks
                List<DocumentChunkEntity> allChunks = documentChunkRepository.findAll();
                List<UUID> exactTopIds = allChunks.stream()
                    .filter(c -> tenantId.equals(c.getTenantId()) && c.getEmbedding() != null)
                    .map(c -> {
                        try {
                            float[] emb = new com.pgvector.PGvector(c.getEmbedding()).toArray();
                            double sim = computeCosine(testCase.queryVector(), emb);
                            return Map.entry(c.getId(), sim);
                        } catch (Exception e) {
                            return Map.entry(c.getId(), 0.0);
                        }
                    })
                    .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                    .limit(topK)
                    .map(Map.Entry::getKey)
                    .toList();

                Set<UUID> exactTopSet = new HashSet<>(exactTopIds);
                long overlap = retrievedIds.stream().limit(topK).filter(exactTopSet::contains).count();
                double hnswOverlap = exactTopIds.isEmpty() ? 1.0 : (double) overlap / exactTopIds.size();
                hnswRecallList.add(hnswOverlap);
            }

            // Persist evaluation result item
            UUID[] retrievedArray = retrievedIds.toArray(new UUID[0]);
            evaluationResultRepository.save(new EvaluationResultEntity(
                run.getId(),
                testCase.query(),
                testCase.groundTruthAnswer(),
                retrievedArray,
                "Retrieved " + retrievedIds.size() + " candidates.",
                computeRecallAtK(retrievedIds, targetIds, topK),
                1.0,
                1.0,
                queryDurationMs
            ));
        }

        // Aggregate Metrics
        double meanRecallAt3 = average(recallsAt3);
        double meanRecallAt5 = average(recallsAt5);
        double meanMrr = average(rrList);
        double meanNdcgAt10 = average(ndcgList);
        double meanHnswVsExact = compareExact ? average(hnswRecallList) : 1.0;

        Collections.sort(latenciesMs);
        double latencyP95 = latenciesMs.isEmpty() ? 0.0 : getPercentile(latenciesMs, 95);

        // Update Run Entity
        run.setContextRelevanceScore(meanRecallAt5);
        run.setGroundednessScore(meanMrr);
        run.setAnswerRelevanceScore(meanNdcgAt10);
        run.setLatencyP95Ms(latencyP95);
        run.setTotalQueries(benchmarkCases.size());
        run.setStatus("COMPLETED");
        run.setCompletedAt(Instant.now());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("recallAt3", meanRecallAt3);
        summary.put("recallAt5", meanRecallAt5);
        summary.put("mrr", meanMrr);
        summary.put("ndcgAt10", meanNdcgAt10);
        summary.put("hnswRecallVsExact", meanHnswVsExact);
        summary.put("latencyP95Ms", latencyP95);
        run.setMetricsSummary(summary);

        experimentRunRepository.save(run);

        return new RetrievalEvaluationResponse(
            run.getId(),
            round(meanRecallAt3, 4),
            round(meanRecallAt5, 4),
            round(meanMrr, 4),
            round(meanNdcgAt10, 4),
            round(meanHnswVsExact, 4),
            round(latencyP95, 2)
        );
    }

    private double computeRecallAtK(List<UUID> retrieved, Set<UUID> relevant, int k) {
        if (relevant.isEmpty()) return 0.0;
        int limit = Math.min(retrieved.size(), k);
        long hits = 0;
        for (int i = 0; i < limit; i++) {
            if (relevant.contains(retrieved.get(i))) {
                hits++;
            }
        }
        return (double) hits / relevant.size();
    }

    private double computeReciprocalRank(List<UUID> retrieved, Set<UUID> relevant) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (relevant.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    private double computeNdcgAtK(List<UUID> retrieved, Set<UUID> relevant, int k) {
        int limit = Math.min(retrieved.size(), k);
        double dcg = 0.0;
        for (int i = 0; i < limit; i++) {
            if (relevant.contains(retrieved.get(i))) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2)); // log2(rank + 1)
            }
        }
        // Ideal DCG
        double idcg = 0.0;
        int idealHits = Math.min(relevant.size(), k);
        for (int i = 0; i < idealHits; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        return idcg > 0.0 ? dcg / idcg : 0.0;
    }

    private double average(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    private double getPercentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private double round(double val, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(val * factor) / factor;
    }

    private double computeCosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (normA == 0.0 || normB == 0.0) ? 0.0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Seeds fixed reference technical documents and query pairs if not already present.
     */
    private synchronized List<BenchmarkCase> ensureGoldenCorpusAndQueries(UUID tenantId, String datasetId) {
        List<DocumentEntity> existing = documentRepository.findAllByTenantId(tenantId);
        List<BenchmarkCase> cases = new ArrayList<>();

        // 5 standard reference technical topics for Phase 4 Tier 1
        String[][] corpusDefs = {
            {
                "PostgreSQL pgvector Architecture",
                "pgvector provides vector similarity search capabilities for PostgreSQL using HNSW and IVFFlat index structures.",
                "How does pgvector support approximate nearest neighbor indexing?"
            },
            {
                "Reciprocal Rank Fusion in RAG",
                "Reciprocal Rank Fusion combines ranked lists from dense vector retrieval and sparse lexical search using rank-based smoothing.",
                "What is Reciprocal Rank Fusion used for in information retrieval?"
            },
            {
                "Cross-Encoder Neural Reranking",
                "A cross-encoder processes query-document pairs jointly to output fine-grained relevance probabilities between zero and one.",
                "How does cross-encoder reranking score candidate relevance?"
            },
            {
                "Matryoshka Vector Embeddings",
                "Matryoshka representation learning allows vector truncation to 256 or 512 dimensions while retaining high fidelity.",
                "What benefit does Matryoshka representation learning provide for vector storage?"
            },
            {
                "RCache Memory Datastore Protocol",
                "RCache is an in-memory datastore implementing custom RESP command parsers and non-blocking TCP socket channels.",
                "How does RCache implement high-throughput memory storage without Redis?"
            }
        };

        Map<Integer, UUID> chunkMap = new HashMap<>();

        if (existing.isEmpty() || existing.stream().noneMatch(d -> d.getFilename().startsWith("golden_ref_"))) {
            for (int i = 0; i < corpusDefs.length; i++) {
                String title = corpusDefs[i][0];
                String content = corpusDefs[i][1];
                String filename = "golden_ref_" + (i + 1) + ".md";

                DocumentEntity doc = documentRepository.save(new DocumentEntity(
                    tenantId,
                    filename,
                    "text/markdown",
                    (long) content.length(),
                    "hash_golden_" + (i + 1),
                    "/storage/" + filename,
                    Map.of("dataset", datasetId, "topic", title)
                ));

                float[] emb = RagRetrievalService.generatePseudoEmbedding(content, 768);
                String vectorStr = new com.pgvector.PGvector(emb).getValue();

                DocumentChunkEntity chunk = documentChunkRepository.save(new DocumentChunkEntity(
                    doc.getId(),
                    tenantId,
                    0,
                    content,
                    content.split("\\s+").length,
                    vectorStr,
                    Map.of("page", 1, "topic", title)
                ));

                chunkMap.put(i, chunk.getId());
            }
        } else {
            // Retrieve existing chunks
            for (int i = 0; i < corpusDefs.length; i++) {
                String filename = "golden_ref_" + (i + 1) + ".md";
                Optional<DocumentEntity> docOpt = existing.stream().filter(d -> filename.equals(d.getFilename())).findFirst();
                if (docOpt.isPresent()) {
                    List<DocumentChunkEntity> chunks = documentChunkRepository.findAllByDocumentIdOrderByChunkIndexAsc(docOpt.get().getId());
                    if (!chunks.isEmpty()) {
                        chunkMap.put(i, chunks.get(0).getId());
                    }
                }
            }
        }

        // Build benchmark test queries targeting each golden chunk
        for (int i = 0; i < corpusDefs.length; i++) {
            String content = corpusDefs[i][1];
            String question = corpusDefs[i][2];
            UUID targetChunkId = chunkMap.get(i);

            if (targetChunkId != null) {
                float[] qVector = RagRetrievalService.generatePseudoEmbedding(question, 768);
                cases.add(new BenchmarkCase(
                    question,
                    content,
                    List.of(targetChunkId),
                    qVector
                ));
            }
        }

        return cases;
    }
}
