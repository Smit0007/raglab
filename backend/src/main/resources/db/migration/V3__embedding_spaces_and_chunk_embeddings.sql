-- ==============================================================================
-- Migration: V3__embedding_spaces_and_chunk_embeddings.sql
-- Description: Phase 6 Multi-Space Embeddings, Quantization & Ingestion Outbox (ADR-010, ADR-015)
-- ==============================================================================

-- 1. Embedding Spaces Registry
CREATE TABLE IF NOT EXISTS embedding_spaces (
    id VARCHAR(64) PRIMARY KEY,
    model_name VARCHAR(128) NOT NULL,
    model_revision VARCHAR(64) NOT NULL,
    dimension INT NOT NULL,
    representation_type VARCHAR(32) NOT NULL,
    distance_metric VARCHAR(32) NOT NULL,
    normalization_policy VARCHAR(32) NOT NULL DEFAULT 'UNIT_L2',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed baseline embedding spaces
INSERT INTO embedding_spaces (id, model_name, model_revision, dimension, representation_type, distance_metric, normalization_policy, is_active)
VALUES
    ('nomic-embed-v1.5-768-dense', 'nomic-embed-text', 'v1.5', 768, 'FLOAT32_DENSE', 'COSINE', 'UNIT_L2', TRUE),
    ('nomic-embed-v1.5-256-mrl', 'nomic-embed-text', 'v1.5', 256, 'MRL_TRUNCATED', 'COSINE', 'UNIT_L2', TRUE),
    ('nomic-embed-v1.5-768-half', 'nomic-embed-text', 'v1.5', 768, 'FLOAT16_HALFVEC', 'COSINE', 'UNIT_L2', TRUE),
    ('nomic-embed-v1.5-768-bin', 'nomic-embed-text', 'v1.5', 768, 'BINARY_QUANTIZED', 'HAMMING', 'UNIT_L2', TRUE)
ON CONFLICT (id) DO NOTHING;

-- 2. Multi-Space Chunk Embeddings Table (Preserving baseline document_chunks.embedding intact)
CREATE TABLE IF NOT EXISTS chunk_embeddings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chunk_id UUID NOT NULL REFERENCES document_chunks(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    space_id VARCHAR(64) NOT NULL REFERENCES embedding_spaces(id) ON DELETE RESTRICT,
    dimension INT NOT NULL,
    representation_type VARCHAR(32) NOT NULL,
    embedding_fp32 vector,
    embedding_half halfvec,
    embedding_bin text,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chunk_space UNIQUE (chunk_id, space_id),
    CONSTRAINT chk_representation_column CHECK (
        (representation_type IN ('FLOAT32_DENSE', 'MRL_TRUNCATED') AND embedding_fp32 IS NOT NULL) OR
        (representation_type = 'FLOAT16_HALFVEC' AND embedding_half IS NOT NULL) OR
        (representation_type = 'BINARY_QUANTIZED' AND embedding_bin IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_chunk_embeddings_lookup ON chunk_embeddings(tenant_id, space_id, chunk_id);

-- 3. Transactional Outbox Table for Asynchronous Ingestion (ADR-015)
CREATE TABLE IF NOT EXISTS ingestion_outbox (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_ingestion_outbox_unpublished ON ingestion_outbox(published, created_at);
