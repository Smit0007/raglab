# RCache: In-Memory Datastore Specification

## 1. Architectural Philosophy & Context

**RCache** is an independent, custom-engineered in-memory key-value datastore designed specifically for high-throughput, sub-millisecond caching in the RAGLab platform.

### Architectural Rules:
1. **NO REDIS IN ANY FORM**: Strict ban on Redis servers, Redis client libraries (Lettuce, Jedis, Redisson), Spring Data Redis, or Redis protocol (RESP) direct reuse.
2. **Independent Datastore**: RCache is built from the ground up with its own binary/text frame protocol (RCache Protocol / **RCP**), memory layout, eviction semantics, and Java client driver.
3. **Fail-Safe Client**: The `rcache-client` incorporates built-in circuit breaking, connection pooling, and connection failover. If RCache crashes or times out, the client immediately degrades without blocking the calling application.

---

## 2. Server Architecture & Internal Design

```
+---------------------------------------------------------------------------------+
|                                 RCache Server                                   |
|                                                                                 |
|  +---------------------------------------------------------------------------+  |
|  |             Network IO Subsystem (Non-blocking TCP Server)                |  |
|  |             - Event Loop / Epoll / Java NIO Selector                      |  |
|  |             - Frame Decoder & Encoder (RCache Protocol - RCP)             |  |
|  +-------------------------------------+-------------------------------------+  |
|                                        |                                        |
|                                        v                                        |
|  +---------------------------------------------------------------------------+  |
|  |                         Command Dispatcher Engine                         |  |
|  |  [PING] [SET] [GET] [DEL] [EXISTS] [EXPIRE] [TTL] [INCR] [MGET] [MSET]   |  |
|  +-------------------------------------+-------------------------------------+  |
|                                        |                                        |
|                                        v                                        |
|  +---------------------------------------------------------------------------+  |
|  |                       In-Memory Storage Engine                            |  |
|  |  - Primary Hash Table: Concurrent Key-Value Registry                      |  |
|  |  - Expiry Wheel / Active TTL Evictor (Min-Heap / Hash Wheel)              |  |
|  |  - Memory Eviction Policy Engine (Volatile-LRU / AllKeys-LRU)             |  |
|  +-------------------------------------+-------------------------------------+  |
|                                        |                                        |
|                                        v                                        |
|  +---------------------------------------------------------------------------+  |
|  |                Persistence & Replication Engine (Roadmap)                 |  |
|  |  - Append-Only Log (AOF) Writer with Fsync Policies (EverySec / Always)   |  |
|  |  - Point-in-time Snapshot Engine (RDB-style Fork/CoW Dump)               |  |
|  |  - Primary-Replica Replication Streamer (Async Offset-based Replication)  |  |
|  +---------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------+
```

---

## 3. RCache Protocol (RCP) Specification

The **RCache Protocol (RCP)** is a clean, framing-efficient, length-prefixed protocol capable of human-readable text inspection while maintaining binary safety.

### 3.1 Frame Types & Byte Identifiers

Every message frame starts with a 1-byte type prefix followed by payload length and `\r\n` delimiters:

| Prefix Byte | Data Type | Encoding Format | Example |
| :--- | :--- | :--- | :--- |
| `+` | Simple String | `+<content>\r\n` | `+OK\r\n`, `+PONG\r\n` |
| `-` | Error Message | `-<ERR_CODE> <message>\r\n` | `-ERR key not found\r\n` |
| `:` | 64-bit Integer | `:<signed_number>\r\n` | `:100\r\n`, `:-1\r\n` |
| `$` | Binary Bulk String | `$<length>\r\n<raw_bytes>\r\n` (Length `-1` represents `NIL`) | `$5\r\nhello\r\n`, `$-1\r\n` |
| `*` | Multi-Array / Batch | `*<element_count>\r\n<element_frames...>` | `*2\r\n$3\r\nGET\r\n$3\r\nkey\r\n` |

### 3.2 Command Request / Response Protocol Examples

#### Example 1: `PING`
- **Request**: `*1\r\n$4\r\nPING\r\n`
- **Response**: `+PONG\r\n`

#### Example 2: `SET key value [EX seconds]`
- **Request**: `*4\r\n$3\r\nSET\r\n$8\r\nuser:100\r\n$4\r\njohn\r\n$2\r\nEX\r\n$2\r\n60\r\n`
- **Response**: `+OK\r\n`

#### Example 3: `GET key` (Cache Hit)
- **Request**: `*2\r\n$3\r\nGET\r\n$8\r\nuser:100\r\n`
- **Response**: `$4\r\njohn\r\n`

#### Example 4: `GET key` (Cache Miss)
- **Request**: `*2\r\n$3\r\nGET\r\n$8\r\nmissing_key\r\n`
- **Response**: `$-1\r\n` (RCP NIL)

---

## 4. Phased Evolution Roadmap

### Version 0.1 (Baseline Core)
- **Core Commands**:
  - `PING [message]` $\rightarrow$ Heartbeat verification
  - `SET <key> <value>` $\rightarrow$ Store string/binary payload
  - `GET <key>` $\rightarrow$ Retrieve payload or NIL
  - `DEL <key> [<key> ...]` $\rightarrow$ Delete one or more keys
  - `EXISTS <key> [<key> ...]` $\rightarrow$ Count existing keys
- **Engine**: Single-threaded command loop or concurrent hash map with non-blocking NIO listener. Pure in-memory (no persistence).

### Version 0.2 (Expiration & Atomic Counters)
- **Commands**:
  - `EXPIRE <key> <seconds>` $\rightarrow$ Set TTL on key
  - `TTL <key>` $\rightarrow$ Query remaining TTL in seconds (`-1` = persistent, `-2` = does not exist)
  - `INCR <key>` $\rightarrow$ Atomically increment 64-bit integer
  - `DECR <key>` $\rightarrow$ Atomically decrement 64-bit integer
  - `MGET <key1> <key2> ...` $\rightarrow$ Batch get
  - `MSET <key1> <val1> ...` $\rightarrow$ Atomic batch set
- **Engine**: Passive eviction on `GET` access + Active eviction background thread running randomized sample sweeps across expired keys.

### Later Advanced Versions (Enterprise Datastore)
- **Persistence (AOF & Snapshots)**:
  - Append-Only File (`rcache.aof`) capturing every write operation with configurable `fsync` (`always`, `everysec`, `no`).
  - Binary point-in-time snapshot (`dump.rcp`) generation via background process.
- **Replication & High Availability**:
  - Primary-Replica asynchronous replication stream.
  - Replication offset tracking (`RCP REPLCONF ACK <offset>`).
  - Automatic failover using leader election (Raft-based sentinel or external coordinator).
- **Clustering & Distributed Sharding**:
  - Hash slot partitioning ($16{,}384$ virtual slots using CRC16).
  - Client-side routing with `MOVED` and `ASK` redirection frames.

---

## 5. RCache Java Client SDK (`rcache-client`) Specification

The `rcache-client` library provides a high-performance Java 21 client designed for Spring Boot auto-configuration.

### 5.1 Architecture & Interfaces

```java
public interface RCacheClient extends AutoCloseable {
    String ping();
    void set(String key, byte[] value);
    void set(String key, byte[] value, Duration ttl);
    byte[] get(String key);
    boolean del(String key);
    boolean exists(String key);
    long incr(String key);
    long ttl(String key);
}
```

### 5.2 Resilience & Fail-Open Semantics
1. **Connection Pool**: Netty or Java NIO socket pool managing keep-alive connections with connection timeouts ($200\text{ms}$) and socket read timeouts ($100\text{ms}$).
2. **Circuit Breaker**: Integrated Resilience4j or custom sliding-window breaker. If $> 50\%$ of RCache requests fail within a 10-second window, the circuit trips to `OPEN`.
3. **Graceful Fallback**: When the circuit is `OPEN`, all `get()` calls immediately return `null` without attempting socket connections, allowing Spring Boot service layers to fetch data directly from PostgreSQL/Ollama.
