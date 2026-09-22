# Phase 0 Implementation Plan: RAGLab Engineering Specification

## 1. Repository Status Confirmation
- **Root Directory**: `d:\Project\raglab`
- **Current State**: Empty git repository (contains only `.git/`).
- **Project Instructions**: Complete Phase 0 Engineering Specification only. No application code, no fake placeholder classes, no microservice premature splitting, absolute zero Redis dependency.

## 2. Target Deliverables
We will generate comprehensive architectural documents and Architectural Decision Records (ADRs) strictly adhering to specifications:

### Core Documents (`docs/`):
1. `docs/requirements.md` - System purpose, Functional & Non-Functional requirements, availability & consistency expectations, operational boundaries.
2. `docs/architecture.md` - Modular monolith high-level architecture, component boundaries, synchronous vs asynchronous flows, failure handling matrices (RCache, Postgres, Kafka, Ollama failure behaviors), observability & telemetry.
3. `docs/data-model.md` - Data ownership matrix, relational schemas (PostgreSQL), vector store model (pgvector), cache key model (RCache), streaming event schemas (Kafka).
4. `docs/api-spec.md` - REST APIs, Server-Sent Events (SSE) streaming contracts, error structures, idempotency keys, evaluation APIs.
5. `docs/rag-spec.md` - Ingestion pipeline, retrieval engine (vector, BM25 hybrid, RRF fusion, reranking), prompt & context assembly, LLM gateway abstraction, citation resolution, streaming generation.
6. `docs/rcache-spec.md` - Custom in-memory datastore architecture, text/binary RESP-like protocol (RCache Protocol / RCP), evolution roadmaps (v0.1, v0.2, and advanced features), persistence (AOF/Snapshot), replication & clustering roadmap, Java client SDK specs with resilience circuit breakers.
7. `docs/evaluation-spec.md` - RAG Triad evaluation (Context Relevance, Groundedness, Answer Relevance), golden datasets, benchmarking harnesses, research paper reproduction framework.
8. `docs/scalability.md` - Monolith scaling, pgvector indexing strategies (HNSW/IVFFlat), Kafka partitioning, RCache sharding roadmap, resource budgets.
9. `docs/security.md` - AuthN/AuthZ, tenant isolation, prompt injection defenses, PII masking, secure credential management.

### Architectural Decision Records (`docs/adr/`):
- `ADR-001-modular-monolith-first.md`
- `ADR-002-postgresql-pgvector.md`
- `ADR-003-kafka-for-async-ingestion.md`
- `ADR-004-rcache-instead-of-redis.md`
- `ADR-005-rest-and-sse.md`
- `ADR-006-ollama-as-initial-llm.md`
- `ADR-007-rcache-protocol-design.md`
- `ADR-008-rcache-persistence-model.md`
- `ADR-009-rcache-replication-model.md`

## 3. Strict Verification & Integrity Checks
- Cross-document validation for zero contradictions.
- Verification of complete zero Redis footprint (libraries, concepts, servers).
- Clear separation of component responsibilities.
- Graceful degradation verification (RCache is non-blocking for core RAG answer path).
