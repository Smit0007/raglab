# ADR-001: Modular Monolith Architecture for Spring Boot Backend

## Status
Accepted

## Context
RAGLab is an ambitious, research-to-production RAG platform integrating vector search, hybrid retrieval, asynchronous processing, evaluation suites, and a custom datastore. When designing such systems, teams frequently jump immediately into microservices architectures (e.g., separate ingestion service, retrieval service, evaluation service, generation gateway). However, microservices introduce severe distributed systems overhead: distributed transaction management, network serialization latency, cross-service debugging complexity, and deployment orchestration friction during early development phases.

## Decision
We decide to build the RAGLab Spring Boot application initially as a **Modular Monolith** with strict internal package boundaries and domain isolation. 

The modular monolith will organize components into high-cohesion modules:
- `raglab-ingestion`
- `raglab-retrieval`
- `raglab-generation`
- `raglab-evaluation`
- `raglab-rcache-client`

Each module communicates with other modules only through well-defined Java interfaces and domain events, avoiding direct circular references.

*Note*: **RCache** remains a separate application daemon, as it is an independent in-memory datastore engineered from scratch.

## Alternatives Considered
1. **Microservices from Day 1**:
   - *Pros*: Independent deployment cycles for ingestion and retrieval.
   - *Cons*: Immense network overhead, difficult refactoring of core RAG abstractions, slow development cycle, costly Kubernetes footprint for local testing.
2. **Unstructured Monolith ("Big Ball of Mud")**:
   - *Pros*: Fastest short-term initial coding speed.
   - *Cons*: Spaghetiffied dependencies making future extraction of services or independent testing impossible.

## Consequences
- **Positive**: High development velocity, in-memory function call performance, simple single-binary deployment and local Docker Compose orchestration.
- **Negative**: Long-term vertical scaling limits if one specific module (e.g., ingestion chunking) requires disproportionate CPU resources compared to retrieval.
- **Mitigation**: Offloading resource-intensive chunking/embedding workloads to asynchronous Kafka worker pools ensures the monolithic web gateway remains responsive.

## Future Reconsideration
We will reconsider decomposing specific modules into standalone microservices if and only if independent scaling, distinct team ownership boundaries, or isolated deployment cadences become an empirical production bottleneck.
