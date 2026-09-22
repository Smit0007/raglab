# RAGLab: Retrieval-Augmented Generation (RAG) Pipeline Specification

## 1. RAG Pipeline Lifecycle

The core RAG query answering execution path in RAGLab follows an 8-stage pipeline:

```
[ User Question ]
       |
       v
+-------------------------------------------------------------+
| Stage 1: Query Processing & Enhancement                     |
| - Preprocessing / Normalization                             |
| - Query Rewriting / Expansion (HyDE / Multi-Query)          |
| - Cache Probing (RCache exact match)                        |
+-------------------------------------------------------------+
       |
       v
+-------------------------------------------------------------+
| Stage 2: Retrieval Strategy Execution                       |
| - Vector Search (pgvector HNSW cosine distance)             |
| - Lexical BM25 (PostgreSQL tsvector full-text search)       |
| - Hybrid Fusion (Reciprocal Rank Fusion - RRF)              |
+-------------------------------------------------------------+
       |
       v
+-------------------------------------------------------------+
| Stage 3: Cross-Encoder Reranking (Optional / Configurable)  |
| - Re-score retrieved Top-K (e.g. 50 chunks down to Top 5)   |
| - Threshold Filtering & Relevance Cutoff                    |
+-------------------------------------------------------------+
       |
       v
+-------------------------------------------------------------+
| Stage 4: Context Building & Token Budget Management         |
| - Chunk Deduplication & Redundancy Removal                  |
| - Context Ordering (Lost-in-the-Middle optimization)        |
| - Token truncation to fit LLM context window                |
+-------------------------------------------------------------+
       |
       v
+-------------------------------------------------------------+
| Stage 5: Prompt Construction & Template Injection           |
| - System Prompt Injection (Defensive against Jailbreaks)    |
| - Grounding Constraints ("Answer ONLY from context")        |
| - Explicit Citation Tokens: [doc_id, chunk_index]           |
+-------------------------------------------------------------+
       |
       v
+-------------------------------------------------------------+
| Stage 6: LLM Gateway & Generation Execution                 |
| - LangChain4j Abstraction Layer                             |
| - Target Engine: Local Ollama (or Pluggable Cloud LLM)      |
| - Streaming Token Generation (SSE) / Sync Completion        |
+-------------------------------------------------------------+
       |
       v
+-------------------------------------------------------------+
| Stage 7: Citation Resolution & Verification                 |
| - Parse bracketed citations [1], [2] in LLM output          |
| - Map citations to source chunk metadata and page numbers   |
| - Groundedness sanity check (Verify claim in context)       |
+-------------------------------------------------------------+
       |
       v
+-------------------------------------------------------------+
| Stage 8: Response Delivery & Asynchronous Cache Write       |
| - Stream completion / JSON response emitted to client       |
| - Store response in RCache asynchronously                   |
+-------------------------------------------------------------+
```

---

## 2. Detailed Pipeline Stages

### Stage 1: Query Processing & Enhancement
- **Normalization**: Strip whitespace, control characters, sanitize query input.
- **Cache Check**: Compute SHA-256 hash of `tenantId + query + retrievalStrategy + model`. Query RCache `rag:query:{tenantId}:{hash}`.
  - If Cache Hit: Return cached answer + citations instantly ($< 5\text{ms}$).
  - If Cache Miss: Proceed with pipeline execution.
- **Query Rewriting (Optional)**: If multi-query or HyDE (Hypothetical Document Embeddings) is enabled, invoke embedding generation with rewritten variations.

### Stage 2: Retrieval Strategies
RAGLab provides pluggable retrieval strategies:

1. **Dense Vector Retrieval (`VECTOR`)**:
   - Compute embedding $\vec{q} = \text{Embed}(query)$ using LangChain4j and Ollama.
   - Execute SQL with pgvector HNSW index:
     ```sql
     SELECT id, document_id, content, metadata, 
            1 - (embedding <=> :queryEmbedding) AS similarity
     FROM document_chunks
     WHERE tenant_id = :tenantId
     ORDER BY embedding <=> :queryEmbedding
     LIMIT :topK;
     ```
2. **Sparse Lexical Retrieval (`BM25`)**:
   - Execute Full-Text Search using PostgreSQL `tsvector` and `tsquery`:
     ```sql
     SELECT id, document_id, content, metadata,
            ts_rank_cd(tsv_content, plainto_tsquery('english', :query)) AS rank_score
     FROM document_chunks
     WHERE tenant_id = :tenantId AND tsv_content @@ plainto_tsquery('english', :query)
     ORDER BY rank_score DESC
     LIMIT :topK;
     ```
3. **Hybrid Retrieval with Reciprocal Rank Fusion (`HYBRID_RRF`)**:
   - Fetch Top-$N$ (e.g. $N=50$) from Dense Vector Search and Top-$N$ from Sparse Lexical Search.
   - Combine scores using standard RRF formula with smoothing parameter $k = 60$:
     $$RRF(d) = \sum_{m \in \{\text{dense}, \text{sparse}\}} \frac{1}{k + \text{rank}_m(d)}$$
   - Sort by combined $RRF(d)$ and take the Top-$K$ (e.g., $K=5$).

### Stage 3: Reranking (Cross-Encoder)
- Pass retrieved candidates $[(query, chunk_1), (query, chunk_2), \dots]$ through a cross-encoder model (e.g., `bge-reranker-large`).
- Produces true semantic joint relevance scores in $[0, 1]$.
- Filters out chunks below `minScore` threshold (e.g. $< 0.40$).

### Stage 4: Context Assembly & Token Management
- **Deduplication**: Chunks sharing $> 90\%$ text overlap from sliding ingestion windows are pruned.
- **Lost-in-the-Middle Ordering**: Place highest-relevance chunks at the very beginning and very end of the context block, placing lower-relevance chunks in the middle.
- **Budget Truncation**: Enforce maximum context token limit (e.g., $3000$ tokens for an $8\text{K}$ context window model).

### Stage 5: System Prompt Template
```
You are RAGLab, an accurate enterprise knowledge assistant.
Answer the user's question relying ONLY on the provided reference context.
Do NOT fabricate facts or extrapolate beyond what is stated.
If the answer cannot be found in the context, explicitly state: "The provided documents do not contain sufficient information to answer this question."

Always cite your sources using bracketed notation like [1], [2] corresponding to the Reference Index.

--- REFERENCE CONTEXT ---
{% for chunk in chunks %}
[Ref {{ loop.index }}] (Document: {{ chunk.documentName }}, Page: {{ chunk.pageNumber }})
{{ chunk.content }}
{% endfor %}
--- END CONTEXT ---

User Question: {{ userQuery }}
Answer:
```

### Stage 6: LLM Gateway & Inference
- Interfaced via **LangChain4j** standard interfaces (`ChatLanguageModel` and `StreamingChatLanguageModel`).
- **Initial Provider**: Local **Ollama** server running `llama3:8b`, `mistral:7b`, or `phi-3`.
- **Pluggable Architecture**: Factory pattern allowing zero-downtime switching to OpenAI (`gpt-4o`), Anthropic (`claude-3-5-sonnet`), Google Gemini (`gemini-1.5-pro`), or self-hosted vLLM endpoints via standard Spring configuration properties.

### Stage 7: Citation Resolution & Verification
- Regex parser matches all `\[(\d+)\]` citations in generated text.
- Replaces or correlates indices with exact chunk metadata:
  - Document Title, Document UUID, Chunk UUID, Page Number, Content Snippet.
- **Verification**: Asserts that every cited index exists in the provided context set. Chunks cited without backing text trigger an evaluation flag for hallucination analysis.

### Stage 8: Delivery & Asynchronous Cache Population
- For streaming requests: SSE chunks emit deltas in real-time. Upon completion, the full assembled answer + citations are asynchronously dispatched to RCache using non-blocking execution (`CompletableFuture.runAsync(...)`).
- Cache failures log a warning metric and do not affect the client response.
