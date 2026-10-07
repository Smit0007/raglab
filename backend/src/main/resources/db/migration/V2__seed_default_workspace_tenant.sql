-- ==============================================================================
-- Migration: V2__seed_default_workspace_tenant.sql
-- Description: Ensures the default workspace tenant exists for frontend and local dev
-- ==============================================================================

INSERT INTO tenants (id, name, api_key_hash)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default Workspace', 'default_dev_key_hash_001')
ON CONFLICT (id) DO NOTHING;
