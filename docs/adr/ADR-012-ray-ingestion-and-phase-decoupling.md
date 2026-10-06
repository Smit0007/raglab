# ADR-012: Python Ray Ingestion and Phase 5/6 Responsibility Decoupling

## Status
Accepted

## Context
Initial roadmap drafts conflated parsing, chunking, embedding generation, and database indexing between Phase 5 and Phase 6. Furthermore, distributed ingestion requires clarity on where Python and Ray fit into the overall system architecture without duplicating Spring Boot responsibilities or scattering storage writes.

## Decision
We formally decouple **Phase 5 (Distributed Ingestion Execution)** and **Phase 6 (Embedding and Indexing Pipelines)** with a strict architectural contract:

### 1. Phase 5 Scope: Distributed Ingestion Execution (Python + Ray)
- **Primary Responsibility**: Raw heterogeneous document transformation into clean, structured, chunked text units.
- **Components**:
  - Kafka consumer consuming `raglab.document.ingestion.v1`
  - Ray core workflow execution:
    - **Ray Tasks**: Stateless CPU-bound document parsing (PDF via PyMuPDF/pdfplumber, DOCX, Markdown, OCR via Tesseract).
    - **Ray Actors**: Stateful tokenizers and chunkers maintaining cached token dictionary weights.
    - **Ray Data**: Streaming chunk dataset processing for large multi-gigabyte document batches.
- **Output Artifact**: Validated chunk records published to Kafka topic `raglab.document.chunks.v1` or written to a staging table. Phase 5 does **not** call embedding models or write to production vector tables.

### 2. Phase 6 Scope: Embedding and Indexing Pipelines
- **Primary Responsibility**: Neural vector generation, lexical index synchronization, and database vector persistence.
- **Components**:
  - Model runtime adapter (Ollama local inference worker or GPU-accelerated batch embedding actor).
  - Production batch writes to PostgreSQL (`document_chunks` table and `chunk_embeddings`).
  - HNSW index build/refresh verification.
  - Final publication transition of document version to `READY`.

## Consequences
- **Positive**: Strict separation between CPU-heavy parsing/chunking and GPU/inference-heavy embedding generation; independent scaling of parsing worker pools and embedding workers; zero double-implementation between phases.
- **Negative**: Requires intermediate event streaming or staging coordination between the parsing output and the embedding generation stage.
