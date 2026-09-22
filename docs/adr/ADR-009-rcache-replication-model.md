# ADR-009: RCache Replication, High Availability & Clustering Evolution Model

## Status
Accepted

## Context
As RAGLab scales to high concurrent query loads and multi-node deployments, a single-instance RCache server becomes a single point of failure (SPOF) for caching and reaches memory/throughput limits. 

We must chart a clear, phased evolutionary path for RCache replication, failover, and clustering so future phases can be implemented without breaking existing client SDK abstractions or wire protocols.

## Decision
We decide to adopt a **three-stage evolution model** for RCache distributed capabilities:

```
[ Stage 1: Single Instance ] (v0.1 & v0.2)
         |
         v
[ Stage 2: Primary-Replica Async Replication & Sentinel Failover ] (Phase 2+)
         |
         v
[ Stage 3: Virtual Hash-Slot Distributed Clustering ] (Phase 3+)
```

### Stage 1: Single Instance (v0.1 - v0.2)
- High-performance standalone daemon with in-memory concurrent storage and fail-open client handling.

### Stage 2: Primary-Replica Async Replication & Sentinel (Phase 2+)
- **Replication Protocol**: Replicas connect to Primary via RCP, send `REPLICATE <primary_ip> <port>`, and receive initial snapshot followed by continuous streaming write commands.
- **Offset Tracking**: Primary and Replicas track logical replication offsets to allow partial resynchronization (`PSYNC`) after brief network disconnects.
- **Failover**: External lightweight health monitor or sentinel process promotes a replica to primary if heartbeats fail for $> 5000\text{ms}$.

### Stage 3: Virtual Hash-Slot Distributed Clustering (Phase 3+)
- **Virtual Slots**: Fixed $16{,}384$ hash slots partitioned across node shards.
- **Key Hashing**: $\text{Slot} = \text{CRC16}(key) \pmod{16384}$.
- **Client Routing**: `rcache-client` maintains slot-to-node topology maps. If a key maps to another shard, the server returns `-MOVED <slot> <target_node_ip:port>`, prompting the client to update its cluster map and retry.

## Alternatives Considered
1. **Raft / Paxos Synchronous Replication on Every Write**:
   - *Pros*: Strict linearizable consistency.
   - *Cons*: High write latency (requires multi-node round-trip per write), overly heavy for an ephemeral caching layer.
2. **Client-Side Consistent Hashing without Server Coordination**:
   - *Pros*: Simple server implementation.
   - *Cons*: Adding/removing nodes causes cache thrashing and inconsistent shard states across different client instances.

## Consequences
- **Positive**: Low write latency (asynchronous replication), incremental engineering complexity, graceful horizontal scaling path up to millions of operations/second.
- **Negative**: Primary failover in Stage 2 has a theoretical window for minimal un-replicated write loss (acceptable for cache data).

## Future Reconsideration
If RCache is ever used for durable transactional data where zero data loss is mandatory, synchronous multi-replica ack modes (`WAIT` command) can be introduced.
