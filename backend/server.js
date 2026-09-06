/**
 * FileSend Private - Production Backend Server
 * Real streaming chunked upload, SHA-256 file integrity verification,
 * secure random code generation, expiration management, and private transfers.
 */

const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const Database = require('better-sqlite3');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '0.0.0.0';
const API_BASE_URL = (process.env.API_BASE_URL || `http://localhost:${PORT}`).replace(/\/$/, '');
const PRIVATE_MODE = process.env.PRIVATE_MODE !== 'false';
const MAX_FILE_SIZE_BYTES = parseInt(process.env.MAX_FILE_SIZE_BYTES || '5368709120', 10); // 5GB default
const STORAGE_DIR = path.resolve(process.env.STORAGE_DIR || './storage/files');
const CHUNKS_DIR = path.resolve(process.env.TEMP_CHUNK_DIR || './storage/chunks');
const DB_PATH = path.resolve(process.env.DB_PATH || './storage/filesend.db');
const CLEANUP_INTERVAL_MS = (parseInt(process.env.CLEANUP_INTERVAL_MINUTES || '10', 10)) * 60 * 1000;

// Ensure storage directories exist
fs.mkdirSync(STORAGE_DIR, { recursive: true });
fs.mkdirSync(CHUNKS_DIR, { recursive: true });
fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });

// Initialize Database
const db = new Database(DB_PATH);
db.pragma('journal_mode = WAL');

// Execute Schema
db.exec(`
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
    expires_at INTEGER,
    download_count INTEGER DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'COMPLETED',
    is_private INTEGER DEFAULT 1
  );

  CREATE TABLE IF NOT EXISTS upload_sessions (
    upload_token TEXT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    original_name TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    total_size INTEGER NOT NULL,
    total_chunks INTEGER NOT NULL,
    uploaded_chunks TEXT NOT NULL DEFAULT '[]',
    expected_checksum TEXT NOT NULL,
    expires_at INTEGER,
    created_at INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'INITIATED'
  );

  CREATE INDEX IF NOT EXISTS idx_files_code ON files(code);
  CREATE INDEX IF NOT EXISTS idx_files_expires_at ON files(expires_at);
  CREATE INDEX IF NOT EXISTS idx_files_status ON files(status);
  CREATE INDEX IF NOT EXISTS idx_upload_sessions_token ON upload_sessions(upload_token);
`);

// Middleware
app.use(helmet({
  contentSecurityPolicy: false, // Allow simple landing page
}));
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Rate limiting
const limiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '60000', 10),
  max: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS || '150', 10),
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests, please slow down.' }
});
app.use('/api/', limiter);

// Multer for chunk uploads (store directly to chunks dir with safe names)
const chunkStorage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, CHUNKS_DIR),
  filename: (req, file, cb) => {
    const uploadToken = req.body.uploadToken || 'unknown';
    const chunkIndex = req.body.chunkIndex || '0';
    // Sanitized filename
    const safeToken = uploadToken.replace(/[^a-zA-Z0-9_-]/g, '');
    cb(null, `${safeToken}_chunk_${chunkIndex}.tmp`);
  }
});
const uploadMulter = multer({
  storage: chunkStorage,
  limits: { fileSize: 50 * 1024 * 1024 } // 50MB max chunk size
});

// Helper: Cryptographically secure unique code generator
function generateSecureCode(length = 6) {
  // Characters excluding easily confused ones (0, O, 1, I)
  const charset = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
  let attempts = 0;
  while (attempts < 100) {
    const bytes = crypto.randomBytes(length);
    let code = '';
    for (let i = 0; i < length; i++) {
      code += charset[bytes[i] % charset.length];
    }
    const existing = db.prepare('SELECT id FROM files WHERE code = ?').get(code);
    const existingSession = db.prepare('SELECT upload_token FROM upload_sessions WHERE code = ?').get(code);
    if (!existing && !existingSession) {
      return code;
    }
    attempts++;
  }
  // Fallback to longer random hex if collision happens
  return crypto.randomBytes(4).toString('hex').toUpperCase();
}

// Helper: Sanitize filename
function sanitizeFilename(filename) {
  if (!filename || typeof filename !== 'string') return 'unnamed_file.bin';
  // Remove directory traversal, null bytes, control characters
  let safe = path.basename(filename).replace(/[\x00-\x1f\x80-\x9f]/g, '').trim();
  safe = safe.replace(/[/\\?%*:|"<>]/g, '_');
  if (safe.length === 0 || safe === '.' || safe === '..') {
    safe = 'transfer_file.bin';
  }
  return safe;
}

// Helper: Compute file SHA-256 streaming
function computeFileSha256(filePath) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = fs.createReadStream(filePath);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('end', () => resolve(hash.digest('hex').toLowerCase()));
    stream.on('error', (err) => reject(err));
  });
}

// Cleanup function for expired files and stale chunks
function runCleanup() {
  const now = Date.now();
  try {
    // 1. Find expired files
    const expiredFiles = db.prepare(`
      SELECT id, code, storage_path FROM files 
      WHERE expires_at IS NOT NULL AND expires_at < ? AND status != 'EXPIRED'
    `).all(now);

    for (const file of expiredFiles) {
      try {
        if (fs.existsSync(file.storage_path)) {
          fs.unlinkSync(file.storage_path);
        }
      } catch (err) {
        console.error(`[Cleanup] Error removing file ${file.storage_path}:`, err.message);
      }
      db.prepare("UPDATE files SET status = 'EXPIRED' WHERE id = ?").run(file.id);
      console.log(`[Cleanup] Expired file ${file.code} removed from storage.`);
    }

    // 2. Find and clean abandoned upload sessions (> 12 hours old)
    const staleSessions = db.prepare(`
      SELECT upload_token FROM upload_sessions 
      WHERE created_at < ?
    `).all(now - 12 * 3600 * 1000);

    for (const session of staleSessions) {
      // Remove any chunk files
      const token = session.upload_token;
      try {
        const files = fs.readdirSync(CHUNKS_DIR);
        for (const f of files) {
          if (f.startsWith(token)) {
            fs.unlinkSync(path.join(CHUNKS_DIR, f));
          }
        }
      } catch (e) {}
      db.prepare('DELETE FROM upload_sessions WHERE upload_token = ?').run(token);
    }
  } catch (err) {
    console.error('[Cleanup Error]', err);
  }
}

// Run periodic cleanup
setInterval(runCleanup, CLEANUP_INTERVAL_MS);

// -------------------------------------------------------------
// REST API ENDPOINTS
// -------------------------------------------------------------

/**
 * GET /api/health
 */
app.get('/api/health', (req, res) => {
  res.json({
    status: 'healthy',
    service: 'FileSend Private',
    version: '1.0.0',
    privateMode: PRIVATE_MODE,
    maxFileSizeBytes: MAX_FILE_SIZE_BYTES,
    serverTime: Date.now()
  });
});

/**
 * POST /api/upload/init
 * Initializes a new upload session
 */
app.post('/api/upload/init', (req, res) => {
  try {
    const { originalName, size, totalChunks, checksum, mimeType, expirationHours } = req.body;

    if (!originalName || typeof originalName !== 'string') {
      return res.status(400).json({ error: 'Invalid or missing originalName' });
    }
    const fileSize = parseInt(size, 10);
    const chunks = parseInt(totalChunks, 10);

    if (isNaN(fileSize) || fileSize <= 0) {
      return res.status(400).json({ error: 'Invalid file size' });
    }
    if (fileSize > MAX_FILE_SIZE_BYTES) {
      return res.status(413).json({ error: `File size exceeds server limit of ${MAX_FILE_SIZE_BYTES / (1024*1024*1024)} GB` });
    }
    if (isNaN(chunks) || chunks < 1) {
      return res.status(400).json({ error: 'Invalid totalChunks count' });
    }
    if (!checksum || typeof checksum !== 'string' || checksum.length < 32) {
      return res.status(400).json({ error: 'Valid SHA-256 checksum required' });
    }

    const safeName = sanitizeFilename(originalName);
    const safeMime = mimeType || 'application/octet-stream';
    const uploadToken = crypto.randomUUID();
    const code = generateSecureCode(6);

    let expiresAt = null;
    const hours = parseInt(expirationHours, 10);
    if (!isNaN(hours) && hours > 0) {
      expiresAt = Date.now() + (hours * 3600 * 1000);
    } else if (expirationHours === 'never' || hours === 0) {
      expiresAt = null;
    } else {
      expiresAt = Date.now() + (24 * 3600 * 1000); // 24 hours default
    }

    const stmt = db.prepare(`
      INSERT INTO upload_sessions (upload_token, code, original_name, mime_type, total_size, total_chunks, uploaded_chunks, expected_checksum, expires_at, created_at, status)
      VALUES (?, ?, ?, ?, ?, ?, '[]', ?, ?, ?, 'INITIATED')
    `);

    stmt.run(uploadToken, code, safeName, safeMime, fileSize, chunks, checksum.toLowerCase(), expiresAt, Date.now());

    res.status(201).json({
      uploadToken,
      code,
      shareUrl: `${API_BASE_URL}/f/${code}`,
      chunkSize: parseInt(process.env.CHUNK_SIZE_BYTES || '4194304', 10),
      expiresAt
    });
  } catch (err) {
    console.error('[Upload Init Error]', err);
    res.status(500).json({ error: 'Failed to initiate upload session' });
  }
});

/**
 * POST /api/upload/chunk
 * Accepts an individual chunk
 */
app.post('/api/upload/chunk', uploadMulter.single('chunk'), (req, res) => {
  try {
    const { uploadToken, chunkIndex } = req.body;
    if (!uploadToken || chunkIndex === undefined) {
      return res.status(400).json({ error: 'uploadToken and chunkIndex are required' });
    }
    const idx = parseInt(chunkIndex, 10);

    const session = db.prepare('SELECT * FROM upload_sessions WHERE upload_token = ?').get(uploadToken);
    if (!session) {
      // Delete uploaded chunk file if session not found
      if (req.file) fs.unlinkSync(req.file.path);
      return res.status(404).json({ error: 'Upload session not found or expired' });
    }

    if (idx < 0 || idx >= session.total_chunks) {
      if (req.file) fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: 'chunkIndex out of bounds' });
    }

    let uploaded = [];
    try {
      uploaded = JSON.parse(session.uploaded_chunks);
    } catch (e) {
      uploaded = [];
    }

    if (!uploaded.includes(idx)) {
      uploaded.push(idx);
    }

    db.prepare(`
      UPDATE upload_sessions 
      SET uploaded_chunks = ?, status = 'UPLOADING'
      WHERE upload_token = ?
    `).run(JSON.stringify(uploaded), uploadToken);

    res.json({
      success: true,
      chunkIndex: idx,
      receivedChunks: uploaded.length,
      totalChunks: session.total_chunks
    });
  } catch (err) {
    console.error('[Chunk Upload Error]', err);
    res.status(500).json({ error: 'Failed to process chunk upload' });
  }
});

/**
 * POST /api/upload/complete
 * Merges all chunks, validates SHA-256, and finalizes the file
 */
app.post('/api/upload/complete', async (req, res) => {
  try {
    const { uploadToken } = req.body;
    if (!uploadToken) {
      return res.status(400).json({ error: 'uploadToken is required' });
    }

    const session = db.prepare('SELECT * FROM upload_sessions WHERE upload_token = ?').get(uploadToken);
    if (!session) {
      return res.status(404).json({ error: 'Upload session not found or already completed' });
    }

    let uploaded = [];
    try {
      uploaded = JSON.parse(session.uploaded_chunks);
    } catch (e) {}

    if (uploaded.length < session.total_chunks) {
      return res.status(400).json({
        error: `Incomplete upload. Received ${uploaded.length} of ${session.total_chunks} chunks.`,
        missingChunks: Array.from({ length: session.total_chunks }, (_, i) => i).filter(i => !uploaded.includes(i))
      });
    }

    // Prepare target storage path
    const fileExt = path.extname(session.original_name) || '';
    const uniqueStorageName = `${session.code}_${Date.now()}${fileExt}`;
    const finalStoragePath = path.join(STORAGE_DIR, uniqueStorageName);

    // Merge chunks in order
    const writeStream = fs.createWriteStream(finalStoragePath);
    for (let i = 0; i < session.total_chunks; i++) {
      const safeToken = uploadToken.replace(/[^a-zA-Z0-9_-]/g, '');
      const chunkPath = path.join(CHUNKS_DIR, `${safeToken}_chunk_${i}.tmp`);
      if (!fs.existsSync(chunkPath)) {
        writeStream.close();
        if (fs.existsSync(finalStoragePath)) fs.unlinkSync(finalStoragePath);
        return res.status(400).json({ error: `Missing chunk file at index ${i}` });
      }
      const chunkData = fs.readFileSync(chunkPath);
      writeStream.write(chunkData);
    }
    await new Promise((resolve) => writeStream.end(resolve));

    // Calculate server SHA-256
    const calculatedChecksum = await computeFileSha256(finalStoragePath);
    if (calculatedChecksum.toLowerCase() !== session.expected_checksum.toLowerCase()) {
      // Checksum mismatch! Delete corrupted file and fail.
      if (fs.existsSync(finalStoragePath)) fs.unlinkSync(finalStoragePath);
      return res.status(409).json({
        error: 'File integrity verification failed. Checksum mismatch.',
        expected: session.expected_checksum,
        actual: calculatedChecksum
      });
    }

    // Clean up temporary chunk files
    for (let i = 0; i < session.total_chunks; i++) {
      const safeToken = uploadToken.replace(/[^a-zA-Z0-9_-]/g, '');
      const chunkPath = path.join(CHUNKS_DIR, `${safeToken}_chunk_${i}.tmp`);
      try {
        if (fs.existsSync(chunkPath)) fs.unlinkSync(chunkPath);
      } catch (e) {}
    }

    // Insert into files database
    const stat = fs.statSync(finalStoragePath);
    const insertStmt = db.prepare(`
      INSERT INTO files (code, upload_token, original_name, extension, mime_type, size, checksum, storage_path, created_at, expires_at, download_count, status, is_private)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'COMPLETED', 1)
    `);

    insertStmt.run(
      session.code,
      session.upload_token,
      session.original_name,
      fileExt,
      session.mime_type,
      stat.size,
      calculatedChecksum,
      finalStoragePath,
      Date.now(),
      session.expires_at
    );

    // Delete session
    db.prepare('DELETE FROM upload_sessions WHERE upload_token = ?').run(uploadToken);

    res.json({
      success: true,
      code: session.code,
      originalName: session.original_name,
      size: stat.size,
      mimeType: session.mime_type,
      checksum: calculatedChecksum,
      shareUrl: `${API_BASE_URL}/f/${session.code}`,
      downloadUrl: `${API_BASE_URL}/api/files/${session.code}/download`,
      expiresAt: session.expires_at
    });
  } catch (err) {
    console.error('[Upload Complete Error]', err);
    res.status(500).json({ error: 'Failed to complete and verify upload' });
  }
});

/**
 * GET /api/files/:code
 * Query metadata for a specific file by its secure code
 */
app.get('/api/files/:code', (req, res) => {
  try {
    const rawCode = req.params.code || '';
    const code = rawCode.trim().toUpperCase();

    if (!code) {
      return res.status(400).json({ error: 'Code is required' });
    }

    const file = db.prepare('SELECT * FROM files WHERE code = ?').get(code);
    if (!file) {
      return res.status(404).json({ error: 'File was not found or has been deleted' });
    }

    // Check expiration
    if (file.expires_at && file.expires_at < Date.now()) {
      // Mark expired
      db.prepare("UPDATE files SET status = 'EXPIRED' WHERE id = ?").run(file.id);
      if (fs.existsSync(file.storage_path)) {
        try { fs.unlinkSync(file.storage_path); } catch (e) {}
      }
      return res.status(410).json({ error: 'File has expired and is no longer available' });
    }

    if (file.status === 'EXPIRED' || file.status === 'DELETED') {
      return res.status(410).json({ error: 'File has expired or was removed' });
    }

    res.json({
      code: file.code,
      originalName: file.original_name,
      extension: file.extension,
      mimeType: file.mime_type,
      size: file.size,
      checksum: file.checksum,
      createdAt: file.created_at,
      expiresAt: file.expires_at,
      downloadCount: file.download_count,
      shareUrl: `${API_BASE_URL}/f/${file.code}`,
      downloadUrl: `${API_BASE_URL}/api/files/${file.code}/download`
    });
  } catch (err) {
    console.error('[Get File Info Error]', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * GET /api/files/:code/download
 * Binary file streaming download with strict headers & original filename preservation
 */
app.get('/api/files/:code/download', (req, res) => {
  try {
    const rawCode = req.params.code || '';
    const code = rawCode.trim().toUpperCase();

    const file = db.prepare('SELECT * FROM files WHERE code = ?').get(code);
    if (!file) {
      return res.status(404).json({ error: 'File was not found' });
    }

    if (file.expires_at && file.expires_at < Date.now()) {
      db.prepare("UPDATE files SET status = 'EXPIRED' WHERE id = ?").run(file.id);
      if (fs.existsSync(file.storage_path)) {
        try { fs.unlinkSync(file.storage_path); } catch (e) {}
      }
      return res.status(410).json({ error: 'File has expired' });
    }

    if (!fs.existsSync(file.storage_path)) {
      return res.status(404).json({ error: 'File data is not found on server storage' });
    }

    // Increment download count
    db.prepare('UPDATE files SET download_count = download_count + 1 WHERE id = ?').run(file.id);

    const stat = fs.statSync(file.storage_path);
    const encodedFilename = encodeURIComponent(file.original_name).replace(/['()]/g, escape);

    res.setHeader('Content-Type', file.mime_type || 'application/octet-stream');
    res.setHeader('Content-Length', stat.size);
    res.setHeader('Content-Disposition', `attachment; filename="${file.original_name.replace(/"/g, '')}"; filename*=UTF-8''${encodedFilename}`);
    res.setHeader('X-File-Checksum', file.checksum);
    res.setHeader('X-File-Name', encodedFilename);
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, proxy-revalidate');

    const readStream = fs.createReadStream(file.storage_path);
    readStream.pipe(res);
  } catch (err) {
    console.error('[Download Error]', err);
    if (!res.headersSent) {
      res.status(500).json({ error: 'Failed to download file' });
    }
  }
});

/**
 * DELETE /api/files/:code
 * Delete file immediately
 */
app.delete('/api/files/:code', (req, res) => {
  try {
    const rawCode = req.params.code || '';
    const code = rawCode.trim().toUpperCase();

    const file = db.prepare('SELECT * FROM files WHERE code = ?').get(code);
    if (!file) {
      return res.status(404).json({ error: 'File not found' });
    }

    if (fs.existsSync(file.storage_path)) {
      try { fs.unlinkSync(file.storage_path); } catch (e) {}
    }
    db.prepare("UPDATE files SET status = 'DELETED' WHERE id = ?").run(file.id);

    res.json({ success: true, message: 'File deleted successfully' });
  } catch (err) {
    console.error('[Delete Error]', err);
    res.status(500).json({ error: 'Failed to delete file' });
  }
});

/**
 * GET /f/:code
 * Web Share Landing Page (Private Mode: noindex, nofollow, clean responsive view)
 */
app.get('/f/:code', (req, res) => {
  const rawCode = req.params.code || '';
  const code = rawCode.trim().toUpperCase();

  const file = db.prepare('SELECT * FROM files WHERE code = ?').get(code);
  const isExpired = file && file.expires_at && file.expires_at < Date.now();
  const notFound = !file || file.status !== 'COMPLETED' || isExpired;

  res.setHeader('X-Robots-Tag', 'noindex, nofollow, noarchive, nosnippet');

  if (notFound) {
    return res.status(404).send(`
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <meta name="robots" content="noindex, nofollow">
        <title>File Not Available — FileSend Private</title>
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
          .card { background: #1e293b; border: 1px solid #334155; border-radius: 16px; padding: 32px; max-width: 440px; width: 100%; text-align: center; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
          h1 { font-size: 22px; margin-bottom: 12px; color: #ef4444; }
          p { color: #94a3b8; font-size: 15px; line-height: 1.5; margin-bottom: 24px; }
          .btn { display: inline-block; background: #3b82f6; color: #fff; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 600; font-size: 14px; }
        </style>
      </head>
      <body>
        <div class="card">
          <h1>⚠️ File Not Available</h1>
          <p>This transfer code <strong>${code}</strong> does not exist, has already expired, or was removed by the sender.</p>
        </div>
      </body>
      </html>
    `);
  }

  const formattedSize = (file.size / (1024 * 1024)).toFixed(2) + ' MB';
  const expiresString = file.expires_at ? new Date(file.expires_at).toLocaleString() : 'Never';

  res.send(`
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <meta name="robots" content="noindex, nofollow">
      <title>Download ${file.original_name} — FileSend Private</title>
      <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #090d16; color: #f8fafc; display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
        .card { background: #131b2e; border: 1px solid #1e293b; border-radius: 20px; padding: 32px; max-width: 480px; width: 100%; box-shadow: 0 20px 40px rgba(0,0,0,0.6); }
        .badge { display: inline-block; background: #1e3a8a; color: #60a5fa; font-size: 12px; font-weight: 700; padding: 4px 12px; border-radius: 999px; margin-bottom: 16px; letter-spacing: 0.5px; }
        h1 { font-size: 20px; word-break: break-all; margin-bottom: 8px; color: #ffffff; }
        .meta { color: #94a3b8; font-size: 14px; margin-bottom: 24px; }
        .info-box { background: #0d1322; border: 1px solid #1e293b; border-radius: 12px; padding: 16px; margin-bottom: 24px; font-size: 13px; }
        .info-row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid #1a233a; }
        .info-row:last-child { border-bottom: none; }
        .info-label { color: #64748b; }
        .info-value { color: #e2e8f0; font-family: monospace; font-weight: 600; word-break: break-all; text-align: right; max-width: 60%; }
        .btn-download { display: block; width: 100%; background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%); color: #fff; text-align: center; text-decoration: none; padding: 14px 20px; border-radius: 12px; font-weight: 700; font-size: 16px; box-shadow: 0 4px 14px rgba(37,99,235,0.4); transition: transform 0.1s ease; }
        .btn-download:active { transform: scale(0.98); }
        .footer { text-align: center; margin-top: 20px; font-size: 12px; color: #475569; }
      </style>
    </head>
    <body>
      <div class="card">
        <div class="badge">FILESEND PRIVATE TRANSFER</div>
        <h1>${file.original_name}</h1>
        <div class="meta">Verified SHA-256 Checksum Included</div>
        
        <div class="info-box">
          <div class="info-row">
            <span class="info-label">File Size</span>
            <span class="info-value">${formattedSize}</span>
          </div>
          <div class="info-row">
            <span class="info-label">File Code</span>
            <span class="info-value">${file.code}</span>
          </div>
          <div class="info-row">
            <span class="info-label">Expires At</span>
            <span class="info-value">${expiresString}</span>
          </div>
          <div class="info-row">
            <span class="info-label">SHA-256</span>
            <span class="info-value" style="font-size: 11px;">${file.checksum.substring(0, 16)}...</span>
          </div>
        </div>

        <a class="btn-download" href="/api/files/${file.code}/download">Download File</a>
        <div class="footer">Encrypted &amp; direct cloud stream transfer</div>
      </div>
    </body>
    </html>
  `);
});

// Start Server
app.listen(PORT, HOST, () => {
  console.log(`=========================================`);
  console.log(` FileSend Private Server running on http://${HOST}:${PORT}`);
  console.log(` API Base URL: ${API_BASE_URL}`);
  console.log(` Private Mode: ${PRIVATE_MODE}`);
  console.log(` Max File Size: ${(MAX_FILE_SIZE_BYTES / (1024*1024*1024)).toFixed(2)} GB`);
  console.log(` Storage Path: ${STORAGE_DIR}`);
  console.log(`=========================================`);
});
