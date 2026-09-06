# FileSend Private — Backend Server Documentation

FileSend Private is a high-performance, private, chunked and resumable file transfer backend supporting any file type (`.apk`, `.zip`, `.pdf`, `.mp4`, `.iso`, etc.) with streaming SHA-256 integrity verification.

## Core Features
- **Chunked Resumable Uploads**: Uploads files of any size (up to configured max, e.g. 5GB+) in small manageable chunks without loading entire files into RAM.
- **SHA-256 Integrity Verification**: Full streaming binary checksum calculation during upload assembly and verified against sender.
- **Cryptographically Secure Random Codes**: 6-character non-ambiguous uppercase codes (e.g. `X7K92P`) instead of sequential database IDs.
- **Strict Private Mode (`PRIVATE_MODE=true`)**: No public file directory, no indexing (`noindex`), and direct code-authenticated access only.
- **Automatic Expiration Cleanup**: Background cron automatically deletes expired physical files and metadata from disk and SQLite.
- **Zero-Corruption Download Streaming**: Preserves binary exactness, filename, and extension with standard `Content-Disposition`.

---

## REST API Specification

### 1. Health Check
- **Endpoint**: `GET /api/health`
- **Response**:
```json
{
  "status": "healthy",
  "service": "FileSend Private",
  "version": "1.0.0",
  "privateMode": true,
  "maxFileSizeBytes": 5368709120,
  "serverTime": 1788198000000
}
```

### 2. Initialize Upload Session
- **Endpoint**: `POST /api/upload/init`
- **Body** (JSON):
```json
{
  "originalName": "MyApplication.apk",
  "size": 26214400,
  "totalChunks": 7,
  "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "mimeType": "application/vnd.android.package-archive",
  "expirationHours": 24
}
```
- **Response** (201 Created):
```json
{
  "uploadToken": "a6b18972-7496-48cb-8f19-b68e7d235889",
  "code": "X7K92P",
  "shareUrl": "https://YOUR_DOMAIN/f/X7K92P",
  "chunkSize": 4194304,
  "expiresAt": 1788284400000
}
```

### 3. Upload File Chunk
- **Endpoint**: `POST /api/upload/chunk`
- **Body** (multipart/form-data):
  - `uploadToken`: Session token string
  - `chunkIndex`: Integer (0-indexed)
  - `chunk`: Binary chunk file data
- **Response**:
```json
{
  "success": true,
  "chunkIndex": 0,
  "receivedChunks": 1,
  "totalChunks": 7
}
```

### 4. Complete & Verify Upload
- **Endpoint**: `POST /api/upload/complete`
- **Body** (JSON):
```json
{
  "uploadToken": "a6b18972-7496-48cb-8f19-b68e7d235889"
}
```
- **Response**:
```json
{
  "success": true,
  "code": "X7K92P",
  "originalName": "MyApplication.apk",
  "size": 26214400,
  "mimeType": "application/vnd.android.package-archive",
  "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "shareUrl": "https://YOUR_DOMAIN/f/X7K92P",
  "downloadUrl": "https://YOUR_DOMAIN/api/files/X7K92P/download",
  "expiresAt": 1788284400000
}
```

### 5. Get File Metadata by Code
- **Endpoint**: `GET /api/files/:code`
- **Response**:
```json
{
  "code": "X7K92P",
  "originalName": "MyApplication.apk",
  "extension": ".apk",
  "mimeType": "application/vnd.android.package-archive",
  "size": 26214400,
  "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "createdAt": 1788198000000,
  "expiresAt": 1788284400000,
  "downloadCount": 0,
  "shareUrl": "https://YOUR_DOMAIN/f/X7K92P",
  "downloadUrl": "https://YOUR_DOMAIN/api/files/X7K92P/download"
}
```

### 6. Stream Download Binary File
- **Endpoint**: `GET /api/files/:code/download`
- Streams binary data with `Content-Disposition: attachment; filename="MyApplication.apk"` and `X-File-Checksum`.

### 7. Delete File
- **Endpoint**: `DELETE /api/files/:code`

---

## Deployment & Setup Instructions

### Prerequisites
- Node.js 18+ LTS
- npm or yarn

### Quick Start
```bash
cd backend
npm install
cp .env.example .env
# Edit .env with your domain & settings
npm start
```

### Running with PM2 or Docker
```bash
npm install -g pm2
pm2 start server.js --name "filesend-backend"
```
