# ADR-016: Retrieval Fusion Scoring and Hybrid Rank Invariants

## Status
Accepted

## Context
RAGLab combines two fundamentally distinct retrieval branches:
1. **Dense Vector Retrieval**: Produces geometric distances (e.g., Cosine Distance in $[0, 2]$, where $0$ is identical; or Cosine Similarity $1 - \text{distance}$ in $[-1, 1]$).
2. **Sparse Lexical Retrieval**: Produces unbounded text matching scores (e.g., BM25 / PostgreSQL `ts_rank_cd` in $[0, \infty)$).
Directly summing or linearly combining raw cosine similarity and `ts_rank_cd` is mathematically invalid because their score distributions, scales, and variances differ drastically across query lengths and corpus statistics.

## Decision
We enforce a strict **Retrieval Fusion Scoring and Hybrid Rank Invariant** based on Reciprocal Rank Fusion (RRF):

### 1. Score Normalization and Rank Separation
1. **Never Mix Raw Scores Directly**: Dense vector similarity scores and lexical BM25 scores must never be summed without rank transformation or calibrated min-max quantile normalization.
2. **Reciprocal Rank Fusion (RRF) Baseline**:
   - Both branches execute independently and return top-$N$ candidates (default $N = 50$).
   - Rank is preserved: each chunk $d$ receives an integer rank position in the dense list ($\text{rank}_{\text{dense}}(d)$) and sparse list ($\text{rank}_{\text{lexical}}(d)$).
   - The unified fusion score is computed via standard RRF with smoothing constant $k = 60$:
     $$RRF(d) = \sum_{m \in \{\text{dense}, \text{lexical}\}} \frac{1}{60 + \text{rank}_m(d)}$$
   - Unretrieved candidates in branch $m$ are assigned $\text{rank}_m = \infty$ (contribution $= 0$).
3. **Deterministic Tie-Breaking**:
   - If two candidates have identical $RRF$ scores, the tie is broken deterministically by:
     1. Dense cosine similarity score (higher wins)
     2. Chunk creation timestamp / chunk index (lower index wins)

### 2. Tenant and Security Invariants During Retrieval
- **Security Invariant**: Tenant isolation (`tenant_id = :tenantId`) and published status (`status = 'READY'`) **must be applied as a hard WHERE clause filter** within both the dense SQL query and lexical SQL query prior to ranking and fusion.
- No fusion or cross-encoder reranking step can resurrect or include chunks from another tenant.

### 3. Partial Failure and Degradation
- If the dense vector index or embedding service fails or times out: the retrieval engine degrades to pure lexical BM25 retrieval without failing the user query.
- If the lexical search branch fails: the retrieval engine degrades to pure dense vector retrieval.
- Only if both branches return empty results does the retrieval pipeline return an empty context set.

## Consequences
- **Positive**: Robust, scale-invariant hybrid fusion; mathematical rigor; seamless fail-open degradation if one retrieval branch is impaired; strict security isolation.
- **Negative**: RRF discards fine-grained margin information between candidate ranks (e.g., rank 1 being vastly better than rank 2). Cross-encoder reranking in Stage 3 can re-score top candidates if fine margins are needed.
