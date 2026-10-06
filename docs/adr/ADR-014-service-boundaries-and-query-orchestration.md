# ADR-014: Service Boundaries and Query Orchestration Architecture

## Status
Accepted

## Context
RAGLab combines a Java 21 Spring Boot modular monolith with Python-based AI ecosystem components (Ray for distributed ingestion, and query orchestration frameworks like Haystack or LlamaIndex).
We must clearly resolve two critical architectural questions:
1. What are the strict service boundaries between Spring Boot and Python?
2. Does the Python Query Service own generation end-to-end, or does Python act as an Evidence Retrieval Engine while Spring Boot acts as the Generation Gateway?

## Decision

### 1. Spring Boot vs. Python Ownership
- **Spring Boot Modular Monolith Owns**:
  - All public, external client REST and SSE APIs (`/api/v1/query`, `/api/v1/documents`, etc.)
  - Authentication (AuthN), Role-Based Access Control (RBAC), and Tenant Context isolation
  - Document lifecycle coordination and state persistence
  - RCache client integration, connection pooling, and circuit breaking
  - Kafka event dispatching for ingestion jobs
  - Enterprise telemetry aggregation and W3C trace propagation
- **Python Services Own**:
  - Distributed ingestion pipelines (Ray cluster workers)
  - Complex retrieval orchestration, sparse-dense reciprocal rank fusion, and cross-encoder neural reranking (Haystack pipeline service)

### 2. Query Orchestration & Generation Boundary Decision
We evaluate two candidate architectural patterns:
- **Option A (Python owns Retrieval + Generation)**: Spring Boot proxies the user query to Python; Python executes retrieval, builds the prompt, calls Ollama, and streams tokens back to Spring Boot.
- **Option B (Python owns Evidence Retrieval; Spring Boot owns Generation Gateway)**: Spring Boot calls the Python Query Service to obtain ranked, filtered, scored context chunks (`Structured Evidence Set`). Spring Boot then owns prompt assembly, LLM Gateway invocation (via LangChain4j), SSE token streaming to clients, citation mapping, and caching in RCache.

**We decide on Option B: Python as Evidence Retrieval Service; Spring Boot as Generation Gateway**:
- **Separation of Concerns**: Generation involves streaming SSE to the client, handling client disconnects, managing RCache query-result caching, and multi-provider failover. Spring Boot is exceptionally well-suited for high-concurrency non-blocking Web MVC / SSE streaming.
- **Observability & Traceability**: Spring Boot maintains the root W3C distributed trace, correlates RCache lookups, and tracks token consumption metrics via Micrometer and Prometheus.
- **Resilience & Pluggability**: If Python retrieval experiences high load, Spring Boot can fall back to direct PostgreSQL pgvector native queries seamlessly.
- **Framework Selection**: **Haystack** is the designated production query orchestration pipeline. **LlamaIndex** serves as an evaluation and benchmark alternative in Phase 4 research harness, not as a second production path.

### 3. Inter-Service Communication Contract
- Spring Boot communicates with Python services via high-performance internal REST/gRPC:
  - Header propagation: `X-Tenant-ID`, `X-Correlation-ID`, `traceparent`
  - Mutual TLS (mTLS) or shared internal token for inter-service authentication
  - Strict timeouts: Retrieval deadline 2000ms; circuit breaker opens upon repeated failure, gracefully falling back to native Spring Data JPA pgvector retrieval.

## Consequences
- **Positive**: Clean architectural boundaries; zero duplication of tenant authentication and caching in Python; LangChain4j and Ollama gateways remain centralized in Spring Boot.
- **Negative**: Adds an internal network round-trip between Spring Boot and the Python retrieval engine.
