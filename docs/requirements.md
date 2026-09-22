# RAGLab: System Requirements Specification

## 1. System Purpose & Vision

**RAGLab** is a research-to-production Retrieval-Augmented Generation (RAG) platform. It serves as a rigorous experimentation laboratory and a resilient, high-throughput production engine. 

RAGLab bridges the gap between empirical LLM/RAG research and production-grade software engineering. It allows researchers and software engineers to ingest heterogeneous documents, experiment with vector and hybrid retrieval techniques, evaluate generation fidelity, reproduce academic benchmarks, and serve real-time AI responses with verifiable citations and fallback guarantees.

---

## 2. Core Stakeholders & Personas

1. **RAG Researchers & Data Scientists**: Execute offline and online evaluation experiments, benchmark custom embedding and reranking strategies, compare prompt topologies, and measure hallucination rates against golden datasets.
2. **Application Developers**: Integrate with RAGLab using standard REST and SSE APIs to build enterprise chat assistants, semantic search interfaces, and knowledge assistants.
3. **Platform & Operations Engineers**: Monitor retrieval latency, vector index performance, cache hit rates, queue lags, LLM inference token throughput, and service resiliency.

---

## 3. Functional Requirements (FR)

### 3.1 Document Ingestion & Preparation
- **FR-ING-01**: Ingest multiple document formats (`.pdf`, `.docx`, `.txt`, `.md`, `.json`, `.csv`).
- **FR-ING-02**: Support asynchronous, event-driven ingestion pipelines decoupled via Apache Kafka.
- **FR-ING-03**: Configurable document splitting and chunking strategies (fixed-size, token-aware, recursive character, semantic markdown/header chunking).
- **FR-ING-04**: Extract and preserve document metadata (document title, author, creation timestamp, hierarchical section header, source URI, page numbers).
- **FR-ING-05**: Generate vector embeddings for text chunks using LangChain4j embedding models.
- **FR-ING-06**: Index chunk vectors and metadata into PostgreSQL with `pgvector` alongside lexical inverted indices.

### 3.2 Search & Retrieval Subsystem
- **FR-RET-01**: Dense vector semantic search via cosine / inner product / L2 distance metrics using pgvector HNSW/IVFFlat indices.
- **FR-RET-02**: Sparse lexical keyword search utilizing PostgreSQL Full-Text Search (`tsvector` / `tsquery`).
- **FR-RET-03**: Hybrid retrieval combining dense vector similarity and sparse BM25 lexical scores using Reciprocal Rank Fusion (RRF) or weighted linear combination.
- **FR-RET-04**: Cross-encoder reranking stage to re-score candidate retrieved chunks before context assembly.
- **FR-RET-05**: Dynamic metadata filtering (e.g., filter by tenant, document category, date range, access level).

### 3.3 Query Processing, Context Building & Generation
- **FR-GEN-01**: Query rewriting and expansion (e.g., query normalization, hypothetical document embeddings (HyDE), step-back prompting).
- **FR-GEN-02**: Context compression, deduplication, and dynamic prompt token-budget management.
- **FR-GEN-03**: LLM generation via Ollama (locally hosted) with pluggable provider support (OpenAI, Anthropic, Gemini, DeepSeek, self-hosted vLLM).
- **FR-GEN-04**: Real-time token streaming to clients using Server-Sent Events (SSE).
- **FR-GEN-05**: Strict citation resolution linking generated claims directly to source chunk IDs, document titles, and page numbers.

### 3.4 Caching Subsystem (RCache)
- **FR-CSH-01**: Query-result caching: Store exact-match query responses with configurable TTLs.
- **FR-CSH-02**: Embedding caching: Cache computed embeddings for frequent queries/chunks to eliminate redundant neural model inference.
- **FR-CSH-03**: Session & Rate Limiting: Manage conversational turn state and rate limit counters.
- **FR-CSH-04**: Communicate exclusively with custom **RCache Server** using custom **RCache Client** over custom TCP/binary protocol (Zero Redis dependencies).
- **FR-CSH-05**: Non-blocking graceful degradation: If RCache is unavailable, RAGLab must route directly to PostgreSQL and LLM without user-facing failure.

### 3.5 Evaluation & Benchmark Framework
- **FR-EVL-01**: Automated evaluation of the **RAG Triad**:
  - *Context Relevance*: Did the retriever fetch relevant information?
  - *Groundedness (Faithfulness)*: Is the LLM response factually supported solely by the retrieved context?
  - *Answer Relevance*: Does the response directly address the user's inquiry?
- **FR-EVL-02**: Golden dataset ingestion and batch offline evaluation runs.
- **FR-EVL-03**: Research reproduction harnesses to benchmark retrieval strategies against published papers.

---

## 4. Non-Functional Requirements (NFR)

### 4.1 Performance & Latency
- **NFR-PERF-01**: Retrieval latency (PostgreSQL + pgvector HNSW top-k search): $< 25\text{ ms}$ for $100{,}000$ chunks ($p95$).
- **NFR-PERF-02**: Time-To-First-Token (TTFT) for streaming responses via Ollama: $< 800\text{ ms}$ ($p95$, local GPU).
- **NFR-PERF-03**: Cached query response latency via RCache: $< 10\text{ ms}$ ($p99$).
- **NFR-PERF-04**: Ingestion throughput: Capable of chunking and embedding at least $100$ document pages per minute asynchronously.

### 4.2 Availability & Resilience
- **NFR-AVL-01**: High availability target: $99.9\%$ uptime for RAGLab core query endpoints.
- **NFR-AVL-02**: Graceful degradation: Caching failure (RCache down) MUST result in automatic cache bypass with zero impact on answer correctness.
- **NFR-AVL-03**: Ingestion resilience: Document ingestion failures MUST be isolated in Kafka Dead Letter Queues (DLQ) without blocking pipeline progress.
- **NFR-AVL-04**: Circuit breaking & timeouts: Calls to Ollama and external LLMs MUST have strict timeouts and fallback mechanisms.

### 4.3 Consistency & Data Integrity
- **NFR-CON-01**: Relational and metadata storage (PostgreSQL) must adhere to ACID transactions.
- **NFR-CON-02**: Ingestion state transitions must be idempotent using document checksums (SHA-256).
- **NFR-CON-03**: Cache consistency: Cache invalidation triggered when underlying documents are updated or deleted.

### 4.4 Security & Governance
- **NFR-SEC-01**: Multi-tenant isolation at database row level and vector index query boundaries.
- **NFR-SEC-02**: Role-Based Access Control (RBAC) on ingestion and administrative APIs.
- **NFR-SEC-03**: Prompt injection and extraction defenses at query preprocessing and generation layers.
- **NFR-SEC-04**: Zero hardcoded secrets; dynamic configuration via environment variables and secret stores.

### 4.5 Observability & Operability
- **NFR-OBS-01**: Distributed tracing using OpenTelemetry across HTTP requests, Kafka consumers, DB queries, RCache commands, and LLM calls.
- **NFR-OBS-02**: Prometheus metrics for retrieval latency, token consumption, cache hit/miss rates, and Kafka lag.
- **NFR-OBS-03**: Structured JSON logging with standardized trace IDs and span IDs.

---

## 5. System Boundary & Explicit Constraints

```
+-----------------------------------------------------------------------------+
|                                RAGLAB SYSTEM                                |
|                                                                             |
|   +---------------------+                  +----------------------------+   |
|   |   Next.js Frontend  | ---- HTTP/SSE -> |  Spring Boot Backend       |   |
|   +---------------------+                  |  (Modular Monolith)        |   |
|                                            +--------------+-------------+   |
|                                                           |                 |
|                   +------------------+--------------------+---+             |
|                   |                  |                        |             |
|                   v                  v                        v             |
|           +---------------+  +---------------+        +---------------+     |
|           |  PostgreSQL   |  | Apache Kafka  |        |    RCache     |     |
|           |  + pgvector   |  | (Async Pipe)  |        |  (Custom DB)  |     |
|           +---------------+  +---------------+        +---------------+     |
|                                      |                                      |
|                                      v                                      |
|                             +-----------------+                             |
|                             |  Ollama (LLM)   |                             |
|                             +-----------------+                             |
+-----------------------------------------------------------------------------+
```

### Architectural Constraints
1. **NO REDIS IN ANY FORM**: Strict ban on Redis server, Lettuce, Jedis, Spring Data Redis, or any Redis protocol compatibility layer.
2. **RCache Autonomy**: RCache is an independent server and custom Java client SDK engineered within this repository ecosystem.
3. **Modular Monolith First**: Spring Boot backend starts as a cleanly packaged modular monolith; microservices will not be prematurely introduced.
4. **Local-First AI with Cloud Pluggability**: Ollama is the baseline local LLM/embedding runtime for offline development and testing, with strict abstractions allowing seamless switching to cloud providers.
