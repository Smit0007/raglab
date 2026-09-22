# RAGLab: Scalability & Performance Strategy

## 1. Scaling Strategy Overview

RAGLab scales horizontally and vertically across its component layers:

```
                                  [ Global Load Balancer ]
                                             |
             +-------------------------------+-------------------------------+
             |                                                               |
             v                                                               v
+--------------------------+                                   +--------------------------+
|  RAGLab Spring Boot Pod  |                                   |  RAGLab Spring Boot Pod  |
|  (Stateless Monolith)    |                                   |  (Stateless Monolith)    |
+------------+-------------+                                   +------------+-------------+
             |                                                              |
             +-----------------------+               +----------------------+
                                     |               |
                                     v               v
                        +----------------------------------------+
                        |       PostgreSQL Primary + Replicas    |
                        |    - pgvector Read Replica Pool        |
                        |    - HNSW Vector Index Partitioning    |
                        +----------------------------------------+
                                     |               |
                                     v               v
                        +----------------------------------------+
                        |             Apache Kafka               |
                        |    - Ingestion Partition Scaling       |
                        +----------------------------------------+
                                     |               |
                                     v               v
                        +----------------------------------------+
                        |        RCache Cluster Roadmap          |
                        |    - Consistent Hash Ring / Slots      |
                        +----------------------------------------+
```

---

## 2. Vector Store Scaling (`pgvector`)

### 2.1 HNSW vs IVFFlat Indexing Strategy
- **Dataset Size $< 500{,}000$ Chunks**: Use **HNSW (Hierarchical Navigable Small World)** with `m = 16`, `ef_construction = 64`.
  - Provides sub-$15\text{ms}$ recall with $> 98\%$ precision.
  - Requires maintaining the HNSW graph in RAM buffer cache (`shared_buffers`).
- **Dataset Size $> 1{,}000{,}000$ Chunks**: Partition `document_chunks` table by `tenant_id` or hash range.
- **Index Memory Budgeting**:
  $$\text{Memory per vector} \approx (\text{Dimensions} \times 4\text{ bytes}) + (2 \times m \times 4\text{ bytes}) \approx (768 \times 4) + (32 \times 4) \approx 3.2\text{ KB}$$
  For $1{,}000{,}000$ chunks, dedicated RAM for vector index $\approx 3.2\text{ GB}$.

---

## 3. Ingestion & Event Stream Scaling (Kafka)

- **Partitioning Strategy**: Topics are partitioned by `tenant_id` or `document_id` hash.
- **Concurrency**: Consumer worker pools scale horizontally in Kubernetes. If $N$ partitions exist on `raglab.document.ingestion.v1`, $N$ consumer worker threads process chunks in parallel.
- **Backpressure**: Kafka consumer throttle prevents overwhelming the local Ollama instance or database during large batch document ingestion.

---

## 4. RCache Scaling & Memory Management

- **Memory Eviction**: Configurable `maxmemory` ceiling (e.g., $4\text{GB}$). When capacity is reached, RCache activates `volatile-lru` or `allkeys-lru` eviction algorithm to prune expired and least recently used keys.
- **Cluster Sharding Roadmap**:
  - $16{,}384$ hash slots distributed across multiple RCache nodes using CRC16 hashing:
    $$\text{Slot} = \text{CRC16}(key) \pmod{16384}$$
  - Client-side routing with connection multiplexing.

---

## 5. LLM Inference & GPU Scaling

- **Local Ollama Scaling**: Multi-GPU allocation with concurrency queuing.
- **Cloud Gateway Auto-Balancing**: Dynamic routing across multiple API keys/regions for external LLMs (OpenAI/Anthropic/Gemini) to prevent token-per-minute (TPM) rate limits.
