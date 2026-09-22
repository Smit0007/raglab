# RAGLab

> Research-to-Production Retrieval-Augmented Generation (RAG) Platform.

RAGLab is a high-performance, resilient RAG platform featuring a Spring Boot modular monolith backend, an independent custom-engineered in-memory datastore (**RCache**), a Next.js frontend, and research reproduction benchmarking suites.

---

## 1. Architectural Principles

- **Modular Monolith**: High cohesion, clean boundaries (`api`, `application`, `domain`, `infrastructure`, `config`).
- **Independent Datastore (RCache)**: Completely independent key-value datastore. **Strictly zero Redis dependencies.**
- **Fail-Open Caching**: Cache is an accelerator; primary query flows degrade gracefully if RCache is offline.
- **Asynchronous Ingestion**: Heavy document processing is decoupled from HTTP request threads.
- **Local-First with Cloud Portability**: Baseline local models via Ollama with pluggable cloud LLM providers via LangChain4j.

---

## 2. Repository Structure

```
raglab/
├── backend/            # Spring Boot 3 / Java 21+ backend foundation
├── rcache/             # Standalone custom in-memory datastore application
├── frontend/           # Next.js 14, TypeScript, Tailwind CSS, shadcn/ui
├── evaluation/         # RAG Triad offline evaluation & paper reproduction foundation
├── infrastructure/     # Docker Compose & container configurations
├── benchmarks/         # Performance & latency benchmark foundation
├── docs/               # System requirements, architecture specs, and ADRs
└── scripts/            # Cross-platform build & orchestration scripts
```

---

## 3. Prerequisites

- **Java Development Kit (JDK)**: Java 21+ (Java 25 compatible)
- **Apache Maven**: 3.9+
- **Node.js**: 20+ (with npm 10+)
- **Docker & Docker Compose**: For containerized development environment

---

## 4. Quick Start & Development

### 4.1 Build & Test Locally

#### Backend & RCache (Java/Maven)
```bash
# Build and run all unit tests
mvn clean test

# Package applications
mvn clean package
```

#### Frontend (Next.js/TypeScript)
```bash
cd frontend
npm install
npm run test       # Run Vitest unit tests
npm run lint       # Run ESLint checks
npm run build      # Compile Next.js production build
```

#### Full Project One-Step Build
- **Windows**: `scripts\build.bat`
- **Linux / macOS**: `./scripts/build.sh`

---

### 4.2 Run Development Environment via Docker Compose

```bash
# Automated build and compose spin up
# Windows:
scripts\run-compose.bat

# Linux / macOS:
./scripts/run-compose.sh

# Or manually:
docker compose -f infrastructure/docker-compose.yml up --build
```

#### Service Endpoints:
- **Backend API**: `http://localhost:8080/actuator/health`
- **RCache Server**: `tcp://localhost:6380`
- **Frontend UI**: `http://localhost:3000`

---

## 5. Documentation & Architectural Decision Records

Full specifications are located in [docs/](file:///d:/Project/raglab/docs):
- [docs/requirements.md](file:///d:/Project/raglab/docs/requirements.md)
- [docs/architecture.md](file:///d:/Project/raglab/docs/architecture.md)
- [docs/data-model.md](file:///d:/Project/raglab/docs/data-model.md)
- [docs/api-spec.md](file:///d:/Project/raglab/docs/api-spec.md)
- [docs/rag-spec.md](file:///d:/Project/raglab/docs/rag-spec.md)
- [docs/rcache-spec.md](file:///d:/Project/raglab/docs/rcache-spec.md)
- [docs/evaluation-spec.md](file:///d:/Project/raglab/docs/evaluation-spec.md)
- [docs/scalability.md](file:///d:/Project/raglab/docs/scalability.md)
- [docs/security.md](file:///d:/Project/raglab/docs/security.md)
- [docs/adr/](file:///d:/Project/raglab/docs/adr) (ADR-001 through ADR-009)
