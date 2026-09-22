# ADR-002: PostgreSQL with pgvector as Unified Primary & Vector Database

## Status
Accepted

## Context
RAG applications require two fundamental types of storage:
1. Relational/Document storage for transactional metadata, documents, users, tenants, audit logs, and evaluation metrics.
2. Vector storage for high-dimensional embedding vectors ($768$ to $1536$ dimensions) with approximate nearest neighbor (ANN) similarity search.

Dedicated vector databases (e.g., Pinecone, Milvus, Qdrant, Weaviate) offer specialized vector search, but introduce significant operational overhead: dual-write consistency problems, network hops between relational data and vector IDs, multi-database backup/restore complexity, and cost overhead.

## Decision
We decide to adopt **PostgreSQL 16+ with the `pgvector` extension** as the single unified primary datastore and vector database for RAGLab.

We will leverage:
- **`pgvector` HNSW indices** (`hnsw (embedding vector_cosine_ops)`) for fast, in-memory approximate nearest neighbor vector search.
- **PostgreSQL Full-Text Search** (`tsvector` / `tsquery` / GIN indices) for sparse keyword search, enabling native hybrid search within single transactional queries.
- Standard relational tables for tenants, documents, ingestion jobs, and evaluation results.

## Alternatives Considered
1. **Specialized Vector DB (Milvus / Qdrant / Pinecone)**:
   - *Pros*: Extreme scale ($> 100\text{M}$ vectors), specialized filtering engines.
   - *Cons*: Two-phase commits needed between PostgreSQL and Vector DB, complex metadata synchronization, increased infrastructure cost.
2. **Elasticsearch / OpenSearch**:
   - *Pros*: Strong BM25 search + basic kNN vector plugin.
   - *Cons*: High JVM memory footprint, complex mapping administration, slower vector similarity search than pgvector HNSW on moderate datasets.

## Consequences
- **Positive**: ACID transactional consistency across chunks, metadata, and vectors in a single database. Simplified operational footprint and local development. Native support for hybrid SQL queries (e.g., filtering metadata, joining tenant data, and computing cosine distances in a single execution plan).
- **Negative**: For extremely large datasets ($> 50\text{M}$ vectors), PostgreSQL requires careful RAM tuning (`shared_buffers`, `maintenance_work_mem`) and table partitioning to maintain sub-second HNSW index performance.

## Future Reconsideration
If document volume exceeds $50\text{M}$ chunks and dedicated vector indexing outgrows PostgreSQL cluster capacity, vector storage can be extracted to a dedicated vector engine without altering the core domain model.
