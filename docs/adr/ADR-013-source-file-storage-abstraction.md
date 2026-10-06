# ADR-013: Source-File Storage Abstraction and Lifecycle

## Status
Accepted

## Context
In Phase 2, `documents.storage_path` was initialized as a database metadata string (e.g. `/data/uploads/filename.pdf`), but durable binary file storage was not yet formalized. The ingestion pipeline requires durable, high-throughput access to uploaded raw files across Spring Boot and distributed Python Ray workers.

## Decision
We define an explicit **Source-File Storage Abstraction** (`BlobStorageService`) supporting unified access across development and production environments:

### 1. Storage Abstraction Contract
```
BlobStorageService:
  - store(tenantId, documentId, versionNumber, inputStream, sizeBytes, sha256) -> storageUri
  - retrieve(storageUri) -> inputStream
  - delete(storageUri) -> boolean
  - verifyIntegrity(storageUri, expectedSha256) -> boolean
```

### 2. Environment Bindings
1. **Development & Docker Compose (`LocalFileSystemBlobStorage`)**:
   - Files are stored in a mounted persistent volume: `/var/raglab/blobs/{tenantId}/{documentId}/v{versionNumber}_{fileHash}.bin`
   - Shared across containers via a named Docker volume (`raglab_blob_data`) mounted into Spring Boot (`/data/blobs`) and Python Ray workers (`/data/blobs`).
2. **Production / Cloud Deployment (`S3CompatibleBlobStorage`)**:
   - S3 / GCS / Azure Blob storage using pre-signed URLs and IAM workload identity.

### 3. File Integrity and Cleanup Policy
- Prior to acknowledging an upload, the storage service computes and verifies the SHA-256 digest against client headers.
- If ingestion terminates in `FAILED` state and is not retried, temporary upload artifacts are marked for garbage collection after 7 days.
- Deleting a document or retiring old versions cascades to source file deletion only after a configurable retention window (default: 30 days) for compliance and disaster recovery.

## Consequences
- **Positive**: Complete portability between local Docker Compose development and enterprise cloud storage; zero reliance on local ephemeral container filesystems.
- **Negative**: Requires shared volume configuration in Docker Compose during development.
