# ADR-004: Custom RCache Datastore Instead of Redis

## Status
Accepted

## Context
A high-performance RAG platform requires fast in-memory caching to avoid redundant LLM generation, store pre-computed embedding vectors, track conversational turn history, and enforce API rate limits. 

Redis is the industry-standard choice for in-memory key-value caching. However, the foundational engineering objective of the RAGLab project is to engineer our own purpose-built in-memory datastore called **RCache**.

**CRITICAL MANDATE**:
RAGLab must NOT use Redis in any form. All Redis dependencies (Lettuce, Jedis, Spring Data Redis, Redisson, Redis server) are strictly forbidden.

## Decision
We decide to engineer and adopt **RCache** as an independent in-memory key-value datastore daemon, alongside a native Java client SDK (`rcache-client`):
1. **RCache Server**: A standalone server process managing concurrent in-memory key-value registries, active TTL evictions, LRU memory policies, and custom network protocols.
2. **RCache Client (`rcache-client`)**: A dedicated Java client integrated into the Spring Boot backend using raw TCP sockets, custom framing decoders, connection pooling, and circuit breakers.
3. **Fail-Open Non-Blocking Dependency Principle**: Caching via RCache is an optimization. If RCache is unavailable, RAGLab must catch the error, open its circuit breaker, and continue serving RAG queries directly from PostgreSQL and the LLM engine without user disruption.

## Alternatives Considered
1. **Redis Server + Lettuce/Jedis**:
   - *Status*: Strictly forbidden by project constraints.
2. **In-JVM Local Cache (Caffeine / Guava)**:
   - *Pros*: Extreme speed (no network serialization).
   - *Cons*: Node-local only; cannot be shared across multiple backend pods, lacks external persistence and clustering roadmap.
3. **Memcached**:
   - *Pros*: Simple key-value store.
   - *Cons*: Lacks custom extensibility, lacks roadmap for advanced RAG vector caching semantics.

## Consequences
- **Positive**: Complete control over protocol design, memory layout, vector-specialized cache operations, zero external license dependencies, deep learning and mastery of low-level systems engineering.
- **Negative**: Engineering burden of building and maintaining a datastore server, protocol parser, connection pool, and failure recovery from scratch.
- **Mitigation**: Phased development roadmap (v0.1 core KV $\rightarrow$ v0.2 TTL/Incr $\rightarrow$ later AOF/Replication) with rigorous automated unit and integration test suites.

## Future Reconsideration
RCache is a core architectural pillar of this project; this decision is non-reconsiderable.
