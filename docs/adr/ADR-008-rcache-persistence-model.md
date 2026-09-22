# ADR-008: RCache Persistence Engine: Hybrid Append-Only File (AOF) and Snapshots

## Status
Accepted

## Context
While RCache operates primarily as an in-memory datastore, enterprise use cases and long-running evaluation benchmarks require durability across server restarts. Caching pre-computed embedding vectors and query evaluation caches represents significant compute time; losing this data on a cold reboot forces expensive re-embedding.

We need a persistence strategy that guarantees crash recovery without degrading sub-millisecond in-memory read/write performance.

## Decision
We decide to adopt a **Hybrid Persistence Model** for RCache (scheduled for implementation in post-v0.2 phases):
1. **Append-Only File (AOF)**:
   - Every mutating command (`SET`, `DEL`, `EXPIRE`, `INCR`) is appended to an append-only log file (`rcache.aof`) using the standard RCP frame format.
   - Configurable `fsync` policies:
     - `always`: Synchronous flush per write (maximum durability, lower throughput).
     - `everysec` (Default): Background thread flushes log every second (bounded 1-second data loss on crash).
     - `no`: Operating system manages flushing (maximum throughput).
2. **Point-In-Time Snapshot (`dump.rcp`)**:
   - Periodic compact snapshot of the full key-value space.
   - Enables fast startup and truncation of the AOF log (AOF Compaction/Rewrite).
3. **Recovery Sequence on Startup**:
   - If snapshot exists, load `dump.rcp` into RAM $\rightarrow$ Replay tail of `rcache.aof` $\rightarrow$ Open TCP listener.

*Note for Phase 0/v0.1*: Initial versions (v0.1 and v0.2) operate in pure in-memory mode. Persistence is designed here for architectural coherence and will be implemented in subsequent phases.

## Alternatives Considered
1. **Snapshots Only (Periodic Dumps)**:
   - *Pros*: Simple implementation, fast recovery.
   - *Cons*: High data loss window between snapshot intervals (e.g. up to 15 minutes of lost cache).
2. **AOF Only without Snapshots**:
   - *Pros*: Simple append log.
   - *Cons*: Log file grows indefinitely; startup recovery time scales linearly with total historical operations.
3. **Pluggable Embedded RocksDB / SQLite**:
   - *Pros*: Off-the-shelf disk engine.
   - *Cons*: Replaces in-memory data structures with disk-centric B-trees/LSMs, compromising raw in-memory throughput and purity of the custom datastore design.

## Consequences
- **Positive**: Controlled durability trade-off, fast crash recovery, deterministic replayability.
- **Negative**: Disk I/O contention during AOF compaction requires background worker threads to avoid stalling the main network event loop.

## Future Reconsideration
As RCache evolves, we may evaluate memory-mapped files (mmap) for zero-copy snapshot serialization.
