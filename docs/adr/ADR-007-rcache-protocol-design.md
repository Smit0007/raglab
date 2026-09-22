# ADR-007: RCache Protocol (RCP) Framing and Network Design

## Status
Accepted

## Context
RCache requires a network communication protocol between the `rcache-server` and client drivers (`rcache-client`). 

Key requirements:
1. Low parsing overhead and minimal memory copying.
2. Binary safety (support caching arbitrary binary vectors, serialized objects, and UTF-8 strings).
3. Human-debuggable wire format over TCP.
4. Clean frame boundaries to prevent TCP packet fragmentation/sticking issues.
5. Absolute independence from Redis RESP.

## Decision
We decide to design and implement the **RCache Protocol (RCP)**, a length-prefixed, type-tagged frame protocol.

Framing specification:
- Every frame starts with a 1-byte type tag:
  - `+` : Simple String (`+OK\r\n`)
  - `-` : Error (`-ERR invalid syntax\r\n`)
  - `:` : Signed 64-bit Integer (`:42\r\n`)
  - `$` : Binary Bulk String (`$<length>\r\n<bytes>\r\n`, or `$-1\r\n` for NIL)
  - `*` : Multi-element array / Command envelope (`*<count>\r\n<element_frames...>`)
- TCP socket listener built with non-blocking NIO / Netty event loops on default port `6380` (distinguishing it cleanly from Redis port `6379`).

## Alternatives Considered
1. **JSON over TCP / HTTP REST**:
   - *Pros*: Human readable, ubiquitous tooling.
   - *Cons*: Severe CPU serialization overhead, high memory allocation per request, inefficient binary vector encoding (Base64 bloat).
2. **Google Protocol Buffers (gRPC)**:
   - *Pros*: Fast binary serialization, strongly typed schemas.
   - *Cons*: Opaque binary wire format, schema compilation overhead, difficult raw CLI debugging (e.g. via netcat/telnet).
3. **Redis RESP2 / RESP3**:
   - *Status*: Strictly forbidden by project constraints.

## Consequences
- **Positive**: Extremely fast parsing, zero-copy byte buffer decoding, human-readable inspection, binary safety for raw vector embeddings, full control over protocol extensions.
- **Negative**: Requires writing custom Netty/NIO frame decoders and encoders in both client and server modules.

## Future Reconsideration
If RCache is extended to support complex vector similarity operators natively inside the datastore in future phases, new typed frames (e.g. `%` for Maps or `~` for Vector Sets) can be introduced backward-compatibly.
