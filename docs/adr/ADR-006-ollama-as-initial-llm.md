# ADR-006: Local Ollama as Initial LLM with Pluggable Model Provider Architecture

## Status
Accepted

## Context
RAGLab serves both as a research laboratory and a production engine. During local development, unit/integration testing, CI pipeline runs, and empirical benchmarking, relying exclusively on paid third-party cloud APIs (OpenAI, Anthropic, Gemini) introduces recurring financial costs, internet connectivity dependencies, latency variability, and privacy/data-leakage concerns.

Conversely, tying the codebase strictly to local models would prevent deploying high-capability models in enterprise production environments.

## Decision
We decide to adopt **Ollama as the default baseline local LLM and embedding runtime**, while encapsulating all model interactions behind **LangChain4j pluggable provider abstractions**.

- Initial local development and automated testing utilize Ollama running `llama3:8b` (generation) and `nomic-embed-text` / `bge-large` (embeddings).
- LangChain4j interfaces (`ChatLanguageModel`, `StreamingChatLanguageModel`, `EmbeddingModel`) decouple application business logic from specific vendor SDKs.
- Configuration switches (`raglab.llm.provider=ollama|openai|anthropic|gemini|vllm`) allow seamless swapping of inference endpoints without code modifications.

## Alternatives Considered
1. **Direct OpenAI / Cloud APIs Only**:
   - *Pros*: Highest initial reasoning quality out-of-the-box.
   - *Cons*: Costly test suites, impossible offline development, data privacy risks with proprietary documents.
2. **Direct vLLM / Hugging Face TEI Only**:
   - *Pros*: Extreme high-throughput GPU serving.
   - *Cons*: Complex local setup requiring dedicated Linux GPU infrastructure, unsuitable for developer laptops on Windows/macOS.
3. **Hardcoding Ollama REST API Client**:
   - *Pros*: Simple HTTP calls.
   - *Cons*: Tight coupling; difficult migration to other LLM vendors.

## Consequences
- **Positive**: Zero-cost, privacy-preserving, fully offline local development and test execution. Seamless pluggability for state-of-the-art cloud providers in production.
- **Negative**: Local generation speed depends on developer workstation hardware (GPU/RAM capacity).
- **Mitigation**: Standardize on quantized small models (`llama3:8b-instruct-q4_K_M`, `phi-3:mini`) for lightweight test environments.

## Future Reconsideration
As local inference technology evolves (e.g. llama.cpp direct bindings, ONNX Runtime, WebGPU), additional local backends can be registered under the LangChain4j abstraction layer.
