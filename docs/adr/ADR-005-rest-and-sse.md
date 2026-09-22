# ADR-005: REST and Server-Sent Events (SSE) for API Communication

## Status
Accepted

## Context
Clients interacting with RAGLab require two distinct communication modes:
1. Standard transactional request/response for document management, status checks, evaluation benchmarking, and batch querying.
2. Real-time token streaming during LLM answer generation to achieve low Time-To-First-Token (TTFT) and an interactive chat experience.

We must select communication protocols that work seamlessly across modern web clients (Next.js/React), backend gateways, and proxy infrastructures.

## Decision
We decide to adopt **REST (over HTTP/2 or HTTP/1.1) for standard API interactions** and **Server-Sent Events (SSE) (`text/event-stream`) for real-time generative streaming**.

- REST endpoints handle CRUD operations, document uploads, and deterministic batch evaluations using standard JSON payloads.
- `POST /api/v1/query/stream` emits Server-Sent Events delivering typed events: `metadata`, `context` (citations), `token` (stream deltas), and `done` (execution summary).

## Alternatives Considered
1. **WebSockets**:
   - *Pros*: Full-duplex bidirectional communication.
   - *Cons*: Stateful connection management, complex load balancing across reverse proxies, unnecessary overhead since RAG token streaming is purely unidirectional (server $\rightarrow$ client).
2. **gRPC / gRPC-Web**:
   - *Pros*: Binary efficiency via Protobuf, strong typing.
   - *Cons*: Browser compatibility friction (requires Envoy proxy or gRPC-web wrappers), complicates simple REST integrations for external developers.
3. **GraphQL with Subscriptions**:
   - *Pros*: Flexible client field selection.
   - *Cons*: Heavy library complexity on both client and Spring Boot backend without clear benefits for RAG pipelines.

## Consequences
- **Positive**: Native browser support via standard `EventSource` or `fetch()` stream readers, simple firewall/proxy traversal, HTTP/2 multiplexing, clear separation of streaming vs transactional APIs.
- **Negative**: SSE is unidirectional; client interruptions or cancelations must be handled via HTTP abort signals or connection drops.

## Future Reconsideration
If complex bidirectional multi-agent chat protocols (e.g. continuous voice or real-time human-in-the-loop tool intervention) are added in the future, WebSockets or gRPC streams may be introduced as an adjunct.
