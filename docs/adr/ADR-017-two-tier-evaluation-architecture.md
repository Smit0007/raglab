# ADR-017: Two-Tier Evaluation Architecture and Phase 4 Minimum Harness

## Status
Accepted

## Context
The revised implementation roadmap requires benchmarking and empirical validation in early phases (such as comparing Haystack vs. LlamaIndex, benchmarking hybrid RRF against dense-only retrieval, and evaluating Matryoshka dimension reduction) before the full enterprise evaluation suite (calibrated LLM-as-a-judge, RAG Triad, adversarial hallucination testing, human golden review) is built in Phase 12.
Without a structured, reproducible evaluation baseline in early phases, architectural choices risk being made based on subjective intuition rather than empirical evidence.

## Decision
We establish a **Two-Tier Evaluation Architecture**:

### 1. Tier 1: Minimum Evaluation Foundation (Phase 4 Deliverable)
- **Purpose**: Provides lightweight, offline, deterministic retrieval and ranking evaluation to support early phase decisions.
- **Scope**:
  - Fixed test corpus (50 standard reference technical documents)
  - Fixed golden query set (100 factual queries with ground-truth chunk IDs)
  - Deterministic evaluation metrics:
    - **Recall@k** (fraction of relevant chunks retrieved in top-$k$, for $k \in \{3, 5, 10\}$)
    - **Mean Reciprocal Rank (MRR)**
    - **Normalized Discounted Cumulative Gain (nDCG@10)**
    - **Retrieval Latency** ($p50, p95, p99$ milliseconds) and RAM footprint
  - Baseline exact vs. approximate HNSW comparison:
    - Computes exact $k$-NN baseline (exhaustive vector scan) to determine exact HNSW recall degradation under different `ef_search` values.
  - Reproducible CLI command: `scripts/eval-retrieval.sh` (or Maven test suite) outputting versioned JSON benchmark summaries.

### 2. Tier 2: Comprehensive Evaluation Engine (Phase 12 Deliverable)
- **Purpose**: Full online and offline generative RAG Triad assessment.
- **Scope**:
  - Faithfulness / Groundedness scoring via calibrated LLM judges and entailment classifiers
  - Answer Relevance and Context Relevance
  - Automated red-teaming and prompt-injection resistance testing
  - Golden dataset management UI, regression alerting, and A/B experiment comparison

### 3. Metric Gate Requirements Before Downstream Decisions
- **Haystack vs. LlamaIndex**: Framework decision requires measured latency, memory overhead, and pipeline developer ergonomics on the Phase 4 Tier 1 harness.
- **Enabling Hybrid RRF**: Requires demonstrating statistically significant improvement in Recall@5 or nDCG@10 over pure dense search ($p < 0.05$).
- **Matryoshka Truncation & Quantization**: Requires verifying that Recall@5 does not drop by more than 2% relative to fp32 baseline before adopting lower dimensions/precision in production defaults.

## Consequences
- **Positive**: Eliminates circular phase dependencies; provides empirical rigor for early decisions without prematurely building a full LLM-judge platform.
- **Negative**: Requires maintaining standard golden test fixtures starting in Phase 4.
