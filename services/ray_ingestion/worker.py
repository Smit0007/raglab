import base64
import hashlib
import io
import re
import uuid
from typing import List, Dict, Any, Tuple
from services.ray_ingestion.models import IngestionTaskRequest, ChunkResult, IngestionTaskResponse

def compute_chunk_uuid(tenant_id: str, document_id: str, chunk_index: int, content_sha256: str) -> str:
    """
    Deterministic UUID derivation according to ADR-011 and ADR-015:
    UUIDv5(namespace, tenant_id:document_id:chunk_index:content_sha256)
    """
    namespace = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8") # DNS namespace
    name = f"{tenant_id}:{document_id}:{chunk_index}:{content_sha256}"
    return str(uuid.uuid5(namespace, name))

def parse_document_content(req: IngestionTaskRequest) -> str:
    """
    Extracts text content based on content_type or provided raw content.
    """
    if req.text_content:
        return req.text_content

    if req.file_bytes_base64:
        raw_bytes = base64.b64decode(req.file_bytes_base64)
        if req.content_type in ("text/plain", "text/markdown", "application/json"):
            return raw_bytes.decode("utf-8", errors="replace")
        elif "pdf" in req.content_type or req.filename.lower().endswith(".pdf"):
            # Simple text extraction fallback or PyMuPDF/pypdf if available
            try:
                import pypdf
                reader = pypdf.PdfReader(io.BytesIO(raw_bytes))
                return "\n".join([page.extract_text() or "" for page in reader.pages])
            except ImportError:
                return raw_bytes.decode("utf-8", errors="ignore")
        else:
            return raw_bytes.decode("utf-8", errors="replace")

    raise ValueError(f"Neither text_content nor file_bytes_base64 supplied for {req.filename}")

def chunk_text(text: str, chunk_size: int, chunk_overlap: int) -> List[Tuple[int, str, int]]:
    """
    Structure-aware sentence/word boundary chunking with overlap.
    Returns: List of (chunk_index, chunk_text, token_count)
    """
    if not text or not text.strip():
        return []

    # Clean text
    normalized = re.sub(r"\s+", " ", text).strip()
    words = normalized.split(" ")
    chunks = []
    chunk_index = 0
    start = 0

    while start < len(words):
        end = min(start + chunk_size, len(words))
        chunk_words = words[start:end]
        chunk_str = " ".join(chunk_words)
        token_count = len(chunk_words) # Approximate token count

        chunks.append((chunk_index, chunk_str, token_count))
        chunk_index += 1

        if end >= len(words):
            break
        start += max(1, chunk_size - chunk_overlap)

    return chunks

def process_ingestion_task(req: IngestionTaskRequest) -> IngestionTaskResponse:
    """
    Synchronous / Ray Task worker function for parsing and deterministic chunking.
    """
    try:
        raw_text = parse_document_content(req)
        raw_chunks = chunk_text(raw_text, req.chunk_size, req.chunk_overlap)

        if not raw_chunks:
            return IngestionTaskResponse(
                job_id=req.job_id,
                document_id=req.document_id,
                tenant_id=req.tenant_id,
                status="FAILED",
                chunks=[],
                total_chunks=0,
                error_message="Document produced zero extractable chunks"
            )

        results: List[ChunkResult] = []
        for idx, content, tokens in raw_chunks:
            content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
            c_id = compute_chunk_uuid(req.tenant_id, req.document_id, idx, content_hash)
            results.append(ChunkResult(
                chunk_id=c_id,
                tenant_id=req.tenant_id,
                document_id=req.document_id,
                chunk_index=idx,
                content=content,
                token_count=tokens,
                content_sha256=content_hash,
                metadata={"source_file": req.filename, "content_type": req.content_type}
            ))

        return IngestionTaskResponse(
            job_id=req.job_id,
            document_id=req.document_id,
            tenant_id=req.tenant_id,
            status="COMPLETED",
            chunks=results,
            total_chunks=len(results)
        )
    except Exception as e:
        return IngestionTaskResponse(
            job_id=req.job_id,
            document_id=req.document_id,
            tenant_id=req.tenant_id,
            status="FAILED",
            chunks=[],
            total_chunks=0,
            error_message=str(e)
        )

