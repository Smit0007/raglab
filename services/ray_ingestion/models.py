import os
import hashlib
import uuid
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field

class IngestionTaskRequest(BaseModel):
    job_id: str
    tenant_id: str
    document_id: str
    filename: str
    content_type: str
    text_content: Optional[str] = None
    file_bytes_base64: Optional[str] = None
    chunk_size: int = 500
    chunk_overlap: int = 50

class ChunkResult(BaseModel):
    chunk_id: str
    tenant_id: str
    document_id: str
    chunk_index: int
    content: str
    token_count: int
    content_sha256: str
    metadata: Dict[str, Any] = Field(default_factory=dict)

class IngestionTaskResponse(BaseModel):
    job_id: str
    document_id: str
    tenant_id: str
    status: str
    chunks: List[ChunkResult]
    total_chunks: int
    error_message: Optional[str] = None

