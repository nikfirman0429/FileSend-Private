-- Database Schema for FileSend Private
-- Supports SQLite and PostgreSQL syntax

CREATE TABLE IF NOT EXISTS files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    upload_token TEXT NOT NULL UNIQUE,
    original_name TEXT NOT NULL,
    extension TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    size INTEGER NOT NULL,
    checksum TEXT NOT NULL,
    storage_path TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER, -- NULL means Never
    download_count INTEGER DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'COMPLETED', -- PENDING, COMPLETED, EXPIRED, DELETED
    is_private INTEGER DEFAULT 1
);

CREATE TABLE IF NOT EXISTS upload_sessions (
    upload_token TEXT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    original_name TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    total_size INTEGER NOT NULL,
    total_chunks INTEGER NOT NULL,
    uploaded_chunks TEXT NOT NULL DEFAULT '[]', -- JSON array of received chunk indices
    expected_checksum TEXT NOT NULL,
    expires_at INTEGER,
    created_at INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'INITIATED' -- INITIATED, UPLOADING, COMPLETED, FAILED
);

-- Essential Indexes for high-speed queries and fast cleanup
CREATE INDEX IF NOT EXISTS idx_files_code ON files(code);
CREATE INDEX IF NOT EXISTS idx_files_expires_at ON files(expires_at);
CREATE INDEX IF NOT EXISTS idx_files_status ON files(status);
CREATE INDEX IF NOT EXISTS idx_upload_sessions_token ON upload_sessions(upload_token);
