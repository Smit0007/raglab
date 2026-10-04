# RAGLab Benchmarks Foundation

This directory contains performance, latency, and throughput benchmarking suites (load testing, vector search index scaling benchmarks, and RCache throughput benchmarks).

## Structure (Planned Evolution)

```
benchmarks/
├── load-tests/          # Distributed load and latency test scripts (e.g. k6 / Gatling)
├── vector-scale/        # pgvector HNSW vs IVFFlat scaling experiments
└── rcache-throughput/   # RCache protocol benchmark harness
```

*Note: Phase 1 establishes the module foundation only. Performance benchmark runners will follow in subsequent phases.*
