# ADR-018: RCache Phased Development Roadmap and Zero-Redis Invariants

## Status
Accepted

## Context
Phase 1 scaffolded an initial Java NIO server foundation for RCache (`RCacheApplication.java`) listening on port 6380, but does not yet implement a functional key/value command engine or protocol parser. Furthermore, the revised master plan condensed or delayed critical milestones for RCache protocol implementation, TTL eviction, fail-open client resilience, replication, and persistence.
We must re-anchor the full RCache lifecycle in the roadmap while strictly preserving the non-negotiable **Zero Redis** invariant.

## Decision
We establish a formalized, dependency-ordered **RCache Implementation Roadmap** across the project phases:

### 1. Invariant Rules (Zero Redis)
1. **Absolute Ban on Redis Artifacts**: Zero Redis dependencies in any pom.xml (Lettuce, Jedis, Redisson, Spring Data Redis, or embedded Redis).
2. **Proprietary Protocol (RCP)**: All client-server communication uses RCache Protocol frames (`+SimpleString`, `-Error`, `:Integer`, `$BulkString`, `*Array`). No RESP compliance layers.
3. **Fail-Open Application Semantics**: RCache is an accelerator. If RCache is unavailable, RAGLab core query operations bypass the cache and query PostgreSQL / LLMs directly with zero user-facing error.

### 2. Phased Roadmap Mapping
- **Phase 1 (Completed)**: Standalone RCache daemon TCP listener foundation.
- **Phase 7 (Core RCache Protocol & Engine - v0.1)**:
  - Byte-level TCP frame decoder & encoder for RCP
  - Non-blocking NIO Event Loop dispatcher
  - In-memory concurrent hash table storage engine
  - Core commands: `PING`, `SET`, `GET`, `DEL`, `EXISTS`
  - Automated protocol compliance test suite
- **Phase 8 (RCache Expiration & Atomic Operations - v0.2)**:
  - Active (background sweep) and passive (on-access) TTL eviction
  - Commands: `EXPIRE`, `TTL`, `INCR`, `DECR`, `MGET`, `MSET`
  - `maxmemory` ceiling and Volatile-LRU / AllKeys-LRU eviction engine
- **Phase 9 (RCache Java Client SDK & Spring Boot Integration)**:
  - `rcache-client` driver with NIO connection pooling
  - Built-in circuit breaker (sliding window fail-open)
  - Spring Boot auto-configuration, health indicator (`/actuator/health`), and cache invalidation hooks
- **Phase 13 (RCache Production Benchmarks & Load Testing)**:
  - Sub-millisecond latency validation at 100,000 ops/sec
  - Chaos injection: kill RCache under load and verify 100% graceful fail-open in Spring Boot
- **Phase 20 (RCache Persistence Engine)**:
  - Append-Only File (`rcache.aof`) logging with `fsync` policies (`always`, `everysec`)
  - Point-in-time snapshot dump (`dump.rcp`) and crash recovery replay
- **Phase 24 (RCache High Availability & Replication)**:
  - Primary-replica asynchronous replication stream
  - Offset tracking (`REPLCONF ACK`) and automated sentinel failover
- **Phase 28 (RCache Distributed Clustering)**:
  - 16,384 virtual hash slots with CRC16 partitioning
  - Client-side routing with `MOVED` and `ASK` redirection frames

## Consequences
- **Positive**: Clear accountability for custom datastore milestones; prevents premature claims of caching capability; guarantees complete architectural autonomy from Redis.
- **Negative**: Requires substantial systems engineering effort to deliver custom datastore features in phases 7, 8, 9, 20, 24, and 28.
