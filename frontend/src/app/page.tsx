'use client'

import React, { useState, useEffect, useCallback } from 'react'

interface Citation {
  citationIndex: number
  chunkId: string
  documentId: string
  documentName: string
  pageNumber: number | null
  snippet: string
  score: number
}

interface QueryResponse {
  queryId: string
  answer: string
  citations: Citation[]
  executionMetrics: {
    retrievalLatencyMs: number
    generationLatencyMs: number
    totalLatencyMs: number
    candidateCount?: number
    cacheHit?: boolean
  }
}

interface EvaluationResponse {
  runId: string
  recallAt3: number
  recallAt5: number
  mrr: number
  ndcgAt10: number
  hnswRecallVsExact: number
  latencyP95Ms: number
}

interface DocumentItem {
  id: string
  filename: string
  contentType: string
  fileSizeBytes: number
  status: string
  totalChunks: number
  pageCount?: number | null
  createdAt: string
  updatedAt?: string | null
}

const DEFAULT_TENANT_ID = '00000000-0000-0000-0000-000000000001'

export default function Home() {
  const [activeTab, setActiveTab] = useState<'documents' | 'query' | 'eval'>('documents')
  const [tenantId, setTenantId] = useState(DEFAULT_TENANT_ID)

  // Document Management state
  const [documents, setDocuments] = useState<DocumentItem[]>([])
  const [docsLoading, setDocsLoading] = useState(false)
  const [uploadLoading, setUploadLoading] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [uploadSuccess, setUploadSuccess] = useState<string | null>(null)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)

  // Query state
  const [query, setQuery] = useState('')
  const [topK, setTopK] = useState(5)
  const [minScore, setMinScore] = useState(0.0)
  const [strategy, setStrategy] = useState('VECTOR')
  const [queryLoading, setQueryLoading] = useState(false)
  const [queryError, setQueryError] = useState<string | null>(null)
  const [queryResult, setQueryResult] = useState<QueryResponse | null>(null)

  // Evaluation state
  const [datasetId, setDatasetId] = useState('golden_docs_v1')
  const [evalStrategy, setEvalStrategy] = useState('VECTOR')
  const [evalTopK, setEvalTopK] = useState(5)
  const [compareExact, setCompareExact] = useState(true)
  const [evalLoading, setEvalLoading] = useState(false)
  const [evalError, setEvalError] = useState<string | null>(null)
  const [evalResult, setEvalResult] = useState<EvaluationResponse | null>(null)

  const fetchDocuments = useCallback(async () => {
    setDocsLoading(true)
    try {
      const res = await fetch('http://localhost:8080/api/v1/documents', {
        headers: {
          'X-Tenant-ID': tenantId,
        },
      })
      if (res.ok) {
        const data = await res.json()
        setDocuments(data)
      }
    } catch {
      // Best-effort fetch in background
    } finally {
      setDocsLoading(false)
    }
  }, [tenantId])

  useEffect(() => {
    fetchDocuments()
  }, [fetchDocuments])

  const handleFileUpload = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedFile) return

    setUploadLoading(true)
    setUploadError(null)
    setUploadSuccess(null)

    const formData = new FormData()
    formData.append('file', selectedFile)

    try {
      const res = await fetch('http://localhost:8080/api/v1/documents/upload', {
        method: 'POST',
        headers: {
          'X-Tenant-ID': tenantId,
        },
        body: formData,
      })

      if (!res.ok) {
        const errText = await res.text()
        throw new Error(`Upload failed (${res.status}): ${errText || res.statusText}`)
      }

      const data = await res.json()
      setUploadSuccess(
        `Successfully ingested "${data.filename}" (${data.totalChunks} chunks, ${data.embeddedChunks} embedded in ${data.processingDurationMs}ms)`
      )
      setSelectedFile(null)
      // Reset input element
      const fileInput = document.getElementById('file-upload-input') as HTMLInputElement | null
      if (fileInput) fileInput.value = ''

      fetchDocuments()
    } catch (err: unknown) {
      setUploadError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setUploadLoading(false)
    }
  }

  const handleDeleteDocument = async (docId: string, filename: string) => {
    if (!confirm(`Are you sure you want to delete "${filename}" and all its chunk embeddings?`)) {
      return
    }

    try {
      const res = await fetch(`http://localhost:8080/api/v1/documents/${docId}`, {
        method: 'DELETE',
        headers: {
          'X-Tenant-ID': tenantId,
        },
      })
      if (res.ok || res.status === 204) {
        fetchDocuments()
      } else {
        alert(`Failed to delete document: ${res.statusText}`)
      }
    } catch (err) {
      alert(`Error deleting document: ${err instanceof Error ? err.message : 'Unknown error'}`)
    }
  }

  const handleQuerySubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!query.trim()) return

    setQueryLoading(true)
    setQueryError(null)
    setQueryResult(null)

    try {
      const res = await fetch('http://localhost:8080/api/v1/query', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': tenantId,
        },
        body: JSON.stringify({
          tenantId: tenantId,
          query: query.trim(),
          retrievalStrategy: strategy,
          topK,
          minScore,
        }),
      })

      if (!res.ok) {
        throw new Error(`Query failed with status ${res.status}: ${res.statusText}`)
      }

      const data: QueryResponse = await res.json()
      setQueryResult(data)
    } catch (err: unknown) {
      setQueryError(err instanceof Error ? err.message : 'An unexpected error occurred')
    } finally {
      setQueryLoading(false)
    }
  }

  const handleRunEvaluation = async () => {
    setEvalLoading(true)
    setEvalError(null)
    setEvalResult(null)

    try {
      const res = await fetch('http://localhost:8080/api/v1/evaluations/run-retrieval', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': tenantId,
        },
        body: JSON.stringify({
          datasetId,
          strategy: evalStrategy,
          topK: evalTopK,
          compareExact,
        }),
      })

      if (!res.ok) {
        throw new Error(`Evaluation failed with status ${res.status}: ${res.statusText}`)
      }

      const data: EvaluationResponse = await res.json()
      setEvalResult(data)
    } catch (err: unknown) {
      setEvalError(err instanceof Error ? err.message : 'An unexpected error occurred')
    } finally {
      setEvalLoading(false)
    }
  }

  return (
    <main className="min-h-screen bg-slate-950 text-slate-100 flex flex-col items-center py-12 px-4 sm:px-6 lg:px-8 font-sans">
      <div className="max-w-5xl w-full space-y-8">
        {/* Header */}
        <header className="border-b border-slate-800 pb-6 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
              <span className="bg-indigo-600 text-white px-2.5 py-0.5 rounded-md text-xl">RAG</span>
              RAGLab
            </h1>
            <p className="mt-1 text-sm text-slate-400">
              Phase 4.1: End-to-End Document Ingestion &amp; RAG Retrieval Pipeline
            </p>
          </div>
          <div className="flex bg-slate-900 border border-slate-800 rounded-lg p-1">
            <button
              onClick={() => setActiveTab('documents')}
              className={`px-4 py-1.5 text-sm font-medium rounded-md transition-colors ${
                activeTab === 'documents'
                  ? 'bg-indigo-600 text-white shadow'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Documents ({documents.length})
            </button>
            <button
              onClick={() => setActiveTab('query')}
              className={`px-4 py-1.5 text-sm font-medium rounded-md transition-colors ${
                activeTab === 'query'
                  ? 'bg-indigo-600 text-white shadow'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              RAG Query Slice
            </button>
            <button
              onClick={() => setActiveTab('eval')}
              className={`px-4 py-1.5 text-sm font-medium rounded-md transition-colors ${
                activeTab === 'eval'
                  ? 'bg-indigo-600 text-white shadow'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Tier 1 Retrieval Evaluation
            </button>
          </div>
        </header>

        {/* Global Tenant Context */}
        <div className="bg-slate-900/40 border border-slate-800/60 rounded-lg px-4 py-2 flex items-center justify-between text-xs text-slate-400">
          <div className="flex items-center gap-2">
            <span className="font-semibold text-slate-300">Active Tenant ID:</span>
            <input
              type="text"
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              className="bg-slate-950 border border-slate-800 rounded px-2 py-0.5 text-slate-200 font-mono text-xs w-72 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>
          <span>PostgreSQL 16 + pgvector (768-dim)</span>
        </div>

        {/* Tab: Documents Management */}
        {activeTab === 'documents' && (
          <section className="space-y-6">
            {/* Upload Box */}
            <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 shadow-sm">
              <h2 className="text-lg font-semibold text-white mb-1">Ingest Single Document</h2>
              <p className="text-xs text-slate-400 mb-4">
                Supported formats: <strong>PDF</strong> (Apache PDFBox), <strong>DOCX</strong> (Apache POI), and <strong>TXT</strong> (UTF-8). Documents are extracted, chunked, embedded with 768-dim vectors, and indexed in PostgreSQL.
              </p>

              <form onSubmit={handleFileUpload} className="space-y-4">
                <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
                  <input
                    id="file-upload-input"
                    type="file"
                    accept=".pdf,.docx,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
                    onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                    className="flex-1 text-sm text-slate-300 file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-slate-800 file:text-indigo-400 hover:file:bg-slate-700 bg-slate-950 border border-slate-800 rounded-lg p-2"
                  />
                  <button
                    type="submit"
                    disabled={uploadLoading || !selectedFile}
                    className="px-5 py-2.5 rounded-lg text-sm font-medium bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white transition-all shadow flex items-center justify-center gap-2"
                  >
                    {uploadLoading ? 'Extracting & Embedding...' : 'Upload & Ingest'}
                  </button>
                </div>
              </form>

              {uploadError && (
                <div className="mt-4 bg-red-950/50 border border-red-800 text-red-200 p-3 rounded-lg text-xs">
                  <span className="font-semibold">Ingestion Failed:</span> {uploadError}
                </div>
              )}

              {uploadSuccess && (
                <div className="mt-4 bg-emerald-950/50 border border-emerald-800 text-emerald-200 p-3 rounded-lg text-xs">
                  <span className="font-semibold">Success:</span> {uploadSuccess}
                </div>
              )}
            </div>

            {/* Document List Table */}
            <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 shadow-sm">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-base font-semibold text-white">
                  Ingested Knowledge Base Documents ({documents.length})
                </h3>
                <button
                  onClick={fetchDocuments}
                  disabled={docsLoading}
                  className="text-xs text-indigo-400 hover:text-indigo-300 underline"
                >
                  {docsLoading ? 'Refreshing...' : 'Refresh List'}
                </button>
              </div>

              {documents.length === 0 ? (
                <div className="text-center py-8 text-slate-500 text-sm border border-dashed border-slate-800 rounded-lg">
                  No documents ingested yet for this tenant. Upload a PDF, DOCX, or TXT file above to get started.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs text-slate-300">
                    <thead className="bg-slate-950 text-slate-400 uppercase font-semibold tracking-wider border-b border-slate-800">
                      <tr>
                        <th className="py-3 px-4">Filename</th>
                        <th className="py-3 px-4">Format</th>
                        <th className="py-3 px-4">Size</th>
                        <th className="py-3 px-4">Status</th>
                        <th className="py-3 px-4">Chunks</th>
                        <th className="py-3 px-4">Date</th>
                        <th className="py-3 px-4 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60">
                      {documents.map((doc) => (
                        <tr key={doc.id} className="hover:bg-slate-900/40">
                          <td className="py-3 px-4 font-medium text-white flex items-center gap-2">
                            <span>📄</span>
                            <span>{doc.filename}</span>
                          </td>
                          <td className="py-3 px-4 text-slate-400">
                            {doc.contentType.includes('pdf') ? 'PDF' : doc.contentType.includes('word') ? 'DOCX' : 'TXT'}
                          </td>
                          <td className="py-3 px-4 text-slate-400">
                            {(doc.fileSizeBytes / 1024).toFixed(1)} KB
                          </td>
                          <td className="py-3 px-4">
                            <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                              doc.status === 'COMPLETED'
                                ? 'bg-emerald-950 text-emerald-400 border border-emerald-800'
                                : doc.status === 'FAILED'
                                ? 'bg-red-950 text-red-400 border border-red-800'
                                : 'bg-amber-950 text-amber-400 border border-amber-800'
                            }`}>
                              {doc.status}
                            </span>
                          </td>
                          <td className="py-3 px-4 font-mono text-indigo-300">
                            {doc.totalChunks} {doc.pageCount ? `(${doc.pageCount} pgs)` : ''}
                          </td>
                          <td className="py-3 px-4 text-slate-500">
                            {new Date(doc.createdAt).toLocaleDateString()}
                          </td>
                          <td className="py-3 px-4 text-right">
                            <button
                              onClick={() => handleDeleteDocument(doc.id, doc.filename)}
                              className="text-red-400 hover:text-red-300 font-medium text-xs hover:underline"
                            >
                              Delete
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </section>
        )}

        {/* Tab 1: Synchronous RAG Query Slice */}
        {activeTab === 'query' && (
          <section className="space-y-6">
            <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 shadow-sm">
              <h2 className="text-lg font-semibold text-white mb-4">Execute Synchronous RAG Query</h2>
              <form onSubmit={handleQuerySubmit} className="space-y-4">
                <div>
                  <label htmlFor="query-input" className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                    Question / Inquiry
                  </label>
                  <textarea
                    id="query-input"
                    rows={3}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-3 text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm"
                    placeholder="e.g. What is the approximate nearest neighbor indexing mechanism in pgvector?"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                  />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                      Strategy
                    </label>
                    <select
                      className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                      value={strategy}
                      onChange={(e) => setStrategy(e.target.value)}
                    >
                      <option value="VECTOR">Dense Vector (pgvector HNSW)</option>
                      <option value="HYBRID_RRF">Hybrid RRF</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                      Top K: {topK}
                    </label>
                    <input
                      type="number"
                      min={1}
                      max={20}
                      className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                      value={topK}
                      onChange={(e) => setTopK(parseInt(e.target.value) || 5)}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                      Min Score: {minScore}
                    </label>
                    <input
                      type="number"
                      step={0.05}
                      min={0.0}
                      max={1.0}
                      className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                      value={minScore}
                      onChange={(e) => setMinScore(parseFloat(e.target.value) || 0.0)}
                    />
                  </div>
                </div>

                <div className="pt-2 flex justify-end">
                  <button
                    type="submit"
                    disabled={queryLoading || !query.trim()}
                    className="inline-flex items-center px-5 py-2.5 rounded-lg text-sm font-medium bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white transition-all shadow"
                  >
                    {queryLoading ? 'Retrieving Chunks...' : 'Run Query'}
                  </button>
                </div>
              </form>
            </div>

            {/* Error state */}
            {queryError && (
              <div className="bg-red-950/50 border border-red-800 text-red-200 p-4 rounded-xl text-sm">
                <span className="font-semibold">Error:</span> {queryError}
              </div>
            )}

            {/* Response result */}
            {queryResult && (
              <div className="space-y-6">
                <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 space-y-4">
                  <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                    <h3 className="text-base font-semibold text-white">Generated Answer</h3>
                    <div className="text-xs text-slate-400 flex gap-3">
                      <span>Query ID: <code className="text-indigo-400">{queryResult.queryId.substring(0, 8)}</code></span>
                      <span>Latency: <strong className="text-emerald-400">{queryResult.executionMetrics.totalLatencyMs} ms</strong></span>
                      <span>Candidates: <strong className="text-indigo-300">{queryResult.executionMetrics.candidateCount ?? 0}</strong></span>
                    </div>
                  </div>
                  <div className="text-slate-200 text-sm whitespace-pre-line leading-relaxed bg-slate-950 p-4 rounded-lg border border-slate-800/80">
                    {queryResult.answer}
                  </div>
                </div>

                {/* Citations */}
                <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6">
                  <h3 className="text-base font-semibold text-white mb-4">
                    Resolved Context Citations ({queryResult.citations.length})
                  </h3>
                  {queryResult.citations.length === 0 ? (
                    <p className="text-sm text-slate-500">No backing citations found matching criteria.</p>
                  ) : (
                    <div className="grid grid-cols-1 gap-3">
                      {queryResult.citations.map((cite) => (
                        <div
                          key={cite.chunkId}
                          className="bg-slate-950 border border-slate-800/80 rounded-lg p-4 text-xs space-y-2"
                        >
                          <div className="flex items-center justify-between text-slate-400">
                            <span className="font-semibold text-indigo-400">
                              [{cite.citationIndex}] {cite.documentName}
                              {cite.pageNumber ? ` (Page ${cite.pageNumber})` : ''}
                            </span>
                            <span className="bg-slate-900 px-2 py-0.5 rounded border border-slate-800 text-slate-300">
                              Relevance: {(cite.score * 100).toFixed(1)}%
                            </span>
                          </div>
                          <p className="text-slate-300 italic">&ldquo;{cite.snippet}&rdquo;</p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}
          </section>
        )}

        {/* Tab 2: Tier 1 Retrieval Evaluation */}
        {activeTab === 'eval' && (
          <section className="space-y-6">
            <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6">
              <h2 className="text-lg font-semibold text-white mb-2">Phase 4 Tier 1 Retrieval Evaluation Harness</h2>
              <p className="text-sm text-slate-400 mb-6">
                Executes deterministic retrieval benchmarks over standard reference corpus fixtures to measure
                Recall@K, MRR, nDCG@10, and HNSW approximation accuracy against exact exhaustive k-NN.
              </p>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                    Benchmark Dataset
                  </label>
                  <input
                    type="text"
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    value={datasetId}
                    onChange={(e) => setDatasetId(e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                    Retrieval Strategy
                  </label>
                  <select
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    value={evalStrategy}
                    onChange={(e) => setEvalStrategy(e.target.value)}
                  >
                    <option value="VECTOR">pgvector HNSW (Cosine)</option>
                    <option value="HYBRID_RRF">Hybrid RRF</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                    Top K Target
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={20}
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    value={evalTopK}
                    onChange={(e) => setEvalTopK(parseInt(e.target.value) || 5)}
                  />
                </div>
              </div>

              <div className="flex items-center justify-between pt-2">
                <label className="flex items-center text-xs text-slate-300 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={compareExact}
                    onChange={(e) => setCompareExact(e.target.checked)}
                    className="rounded border-slate-700 bg-slate-950 text-indigo-600 focus:ring-indigo-500 mr-2"
                  />
                  Compare HNSW against exact exhaustive k-NN baseline
                </label>

                <button
                  onClick={handleRunEvaluation}
                  disabled={evalLoading}
                  className="px-5 py-2.5 rounded-lg text-sm font-medium bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white transition-all shadow"
                >
                  {evalLoading ? 'Computing Metrics...' : 'Run Tier 1 Benchmark'}
                </button>
              </div>
            </div>

            {/* Error state */}
            {evalError && (
              <div className="bg-red-950/50 border border-red-800 text-red-200 p-4 rounded-xl text-sm">
                <span className="font-semibold">Evaluation Error:</span> {evalError}
              </div>
            )}

            {/* Metric Results Card */}
            {evalResult && (
              <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-6 space-y-6">
                <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                  <div>
                    <h3 className="text-base font-semibold text-white">Benchmark Report Summary</h3>
                    <p className="text-xs text-slate-400">Run ID: {evalResult.runId}</p>
                  </div>
                  <span className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 px-2.5 py-1 rounded text-xs font-semibold">
                    BENCHMARK COMPLETED
                  </span>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
                  <div className="bg-slate-950 border border-slate-800 rounded-lg p-4 text-center">
                    <span className="block text-xs text-slate-400 uppercase tracking-wider mb-1">Recall@3</span>
                    <span className="text-xl font-bold text-white">{(evalResult.recallAt3 * 100).toFixed(1)}%</span>
                  </div>
                  <div className="bg-slate-950 border border-slate-800 rounded-lg p-4 text-center">
                    <span className="block text-xs text-slate-400 uppercase tracking-wider mb-1">Recall@5</span>
                    <span className="text-xl font-bold text-indigo-400">{(evalResult.recallAt5 * 100).toFixed(1)}%</span>
                  </div>
                  <div className="bg-slate-950 border border-slate-800 rounded-lg p-4 text-center">
                    <span className="block text-xs text-slate-400 uppercase tracking-wider mb-1">MRR</span>
                    <span className="text-xl font-bold text-white">{evalResult.mrr.toFixed(3)}</span>
                  </div>
                  <div className="bg-slate-950 border border-slate-800 rounded-lg p-4 text-center">
                    <span className="block text-xs text-slate-400 uppercase tracking-wider mb-1">nDCG@10</span>
                    <span className="text-xl font-bold text-white">{evalResult.ndcgAt10.toFixed(3)}</span>
                  </div>
                  <div className="bg-slate-950 border border-slate-800 rounded-lg p-4 text-center">
                    <span className="block text-xs text-slate-400 uppercase tracking-wider mb-1">HNSW vs Exact</span>
                    <span className="text-xl font-bold text-emerald-400">{(evalResult.hnswRecallVsExact * 100).toFixed(1)}%</span>
                  </div>
                  <div className="bg-slate-950 border border-slate-800 rounded-lg p-4 text-center">
                    <span className="block text-xs text-slate-400 uppercase tracking-wider mb-1">Latency p95</span>
                    <span className="text-xl font-bold text-amber-400">{evalResult.latencyP95Ms.toFixed(1)} ms</span>
                  </div>
                </div>
              </div>
            )}
          </section>
        )}
      </div>
    </main>
  )
}
