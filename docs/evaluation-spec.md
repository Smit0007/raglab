# RAGLab: Evaluation & Research Benchmark Specification

## 1. Evaluation Purpose & Core Principles

RAGLab's evaluation framework is designed to provide quantitative, reproducible metrics for Retrieval-Augmented Generation systems. It enables researchers to:
1. Prevent hallucinations and measure factual groundedness.
2. Measure the retrieval fidelity of vector versus hybrid strategies.
3. Quantify latency vs. accuracy tradeoffs across different LLMs and embedding models.
4. Reproduce empirical results from leading academic RAG papers.

---

## 2. The RAG Triad Evaluation Framework

The evaluation engine computes three canonical metrics for every test case:

```
                  +-------------------------+
                  |      User Question      |
                  +------------+------------+
                               |
                Context        |        Answer
               Relevance       |       Relevance
                               |
                               v
+------------------------+ <------- > +-------------------------+
|   Retrieved Context    |            |   Generated Response    |
+------------------------+            +-------------------------+
                     \                    /
                      \                  /
                       \                /
                        v              v
                         [ Groundedness ]
```

### 2.1 Metric Definitions & Scoring Formats

| Metric | Target Question | Scoring Range | Evaluation Method |
| :--- | :--- | :--- | :--- |
| **Context Relevance** | Did the retrieval stage fetch only chunks relevant to the user query, minimizing noise? | $[0.0, 1.0]$ | LLM-as-a-judge / Cross-encoder scoring extracted sentences vs. question. |
| **Groundedness (Faithfulness)** | Can every factual claim in the generated response be inferred directly from the retrieved context? | $[0.0, 1.0]$ | LLM-as-a-judge extracting response claims and verifying entailment against retrieved context. |
| **Answer Relevance** | Does the generated response directly and completely address the user's inquiry? | $[0.0, 1.0]$ | Cosine similarity of question embeddings + LLM-judge intent satisfaction score. |

---

## 3. Golden Benchmark Datasets & Reproduction Framework

### 3.1 Supported Dataset Formats
Golden datasets are ingested as JSONL structures:
```json
{
  "datasetId": "finance_rag_benchmark_v1",
  "testCases": [
    {
      "id": "tc_001",
      "question": "What was the year-over-year revenue growth for ACME Corp in Q3 2025?",
      "groundTruthAnswer": "ACME Corp reported a 14.2% YoY revenue growth in Q3 2025.",
      "groundTruthChunkIds": ["chunk_uuid_8841"],
      "expectedMetadataFilters": {"documentCategory": "financial_report"}
    }
  ]
}
```

### 3.2 Research Paper Reproduction Experiments

| Paper / Technique | Architectural Target | Metric Target |
| :--- | :--- | :--- |
| **HyDE (Hypothetical Document Embeddings)** (Gao et al.) | Evaluates whether zero-shot generated answer embeddings improve recall over raw question embeddings. | Measure Context Recall @ 5 vs standard Dense Retrieval. |
| **RRF (Reciprocal Rank Fusion)** (Cormack et al.) | Benchmark pure Dense Vector (HNSW) vs Sparse (BM25) vs Hybrid (RRF). | Measure Mean Reciprocal Rank (MRR) and NDCG@10. |
| **Lost in the Middle** (Liu et al.) | Tests impact of context chunk positioning on LLM factual extraction. | Measure Groundedness when relevant chunk is at Position 1 vs Middle vs End. |

---

## 4. Evaluation Execution Pipeline

1. **Benchmark Dispatcher**: Loads golden dataset from PostgreSQL `evaluation_runs`.
2. **Strategy Matrix Runner**: For every test case, executes the query across:
   - Retrieval variations: `[VECTOR, BM25, HYBRID_RRF]`
   - Reranking variations: `[NONE, CROSS_ENCODER]`
   - LLM models: `[llama3:8b, mistral:7b, gpt-4o-mini]`
3. **Judge Execution**: Evaluates generated answer against the RAG Triad using a designated Judge LLM (or high-accuracy cross-encoder).
4. **Aggregation & Reporting**: Stores per-case results in `evaluation_results` and calculates aggregate metrics:
   - Average Faithfulness
   - Average Context Recall
   - Latency $p50$, $p95$, $p99$
   - Total Token Cost
