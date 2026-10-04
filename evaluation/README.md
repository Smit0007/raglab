# RAGLab Evaluation Foundation

This directory houses the offline evaluation framework, RAG Triad assessment harnesses, and research paper reproduction benchmarks as defined in [docs/evaluation-spec.md](file:///d:/Project/raglab/docs/evaluation-spec.md).

## Structure (Planned Evolution)

```
evaluation/
├── datasets/            # Golden benchmark datasets (JSONL format)
├── harnesses/           # Evaluation runners and LLM-as-a-judge scoring
└── results/             # Raw and aggregated experiment outputs
```

*Note: Phase 1 establishes the module foundation only. Evaluation implementations will follow in subsequent phases.*
