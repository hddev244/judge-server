# AI Agent Integration Guide

Tài liệu này dành cho **AI agents** (Claude, GPT, Cursor, Copilot, v.v.) và các **developer** muốn tích hợp với hệ thống Judge Server đang triển khai.

---

## Kiến trúc tổng quan

```
┌─────────────────────────┐      ┌──────────────────────────────┐
│  Next.js Admin (FE)     │      │   Client App / User FE       │
│  /admin pages           │      │   (React, Vue, mobile…)      │
│  calls /api/proxy/*     │      │   calls /api/proxy/* (proxy) │
└────────────┬────────────┘      └──────────────┬───────────────┘
             │  HTTP (server-side)               │  HTTP (server-side)
             ▼                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Judge Server (Spring Boot)                  │
│                     http://localhost:8433                       │
│                     Auth: X-API-Key header (per-client)        │
│                                                                 │
│  REST API  ·  WebSocket (STOMP/SockJS)  ·  Actuator           │
└──────────────┬──────────────────────────────────────────────────┘
               │ Docker SDK
               ▼
        Sandbox containers (gcc:13, eclipse-temurin:21, python:3.12-slim)
```

**Quy tắc bắt buộc**: API key **không bao giờ** được để trong FE (browser). Mọi request đến judge server đều phải đi qua proxy tầng backend (Node.js, Next.js API routes, Spring, v.v.). API key nằm trong biến môi trường server-side.

---

## 1. Authentication

Mọi endpoint (trừ public endpoints) yêu cầu header:

```
X-API-Key: <your_api_key>
```

### Phân quyền

| Quyền | Điều kiện | Ví dụ endpoint |
|-------|-----------|----------------|
| Public | Không cần key | `/api/v1/leaderboard`, `/api/v1/contests`, WebSocket |
| User | Key hợp lệ bất kỳ | Submit bài, xem submission |
| Admin | Key có `is_admin = true` | Tạo/sửa problem, quản lý contest, API keys |

### Tạo API key đầu tiên (bootstrap)

Nếu chưa có key nào, insert thẳng vào DB:

```sql
INSERT INTO api_keys (key, client_name, is_active, is_admin, rate_limit_per_hour)
VALUES ('sk_your_secret_key', 'admin-bootstrap', true, true, 10000);
```

Hoặc dùng admin.html tại `http://<server>/admin.html`.

---

## 2. Base URL & Headers

```
Base URL:  http://<server>:8433     (internal)
           https://<domain>         (nếu đã có nginx + SSL)

Required headers:
  X-API-Key: <key>
  Content-Type: application/json   (với POST/PUT body)
```

---

## 3. Proxy pattern cho Next.js

### `lib/judgeClient.ts`

```ts
const JUDGE_BASE = process.env.JUDGE_BASE_URL!;   // server-side only
const JUDGE_KEY  = process.env.JUDGE_API_KEY!;    // server-side only

export async function judgeApi<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(JUDGE_BASE + path, {
    ...init,
    headers: {
      'X-API-Key': JUDGE_KEY,
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  });
  if (res.status === 204) return null as T;
  const data = await res.json();
  if (!res.ok) throw new Error(data.message ?? `HTTP ${res.status}`);
  return data as T;
}
```

### `app/api/judge/[...path]/route.ts`

```ts
import { judgeApi } from '@/lib/judgeClient';
import { NextRequest } from 'next/server';

export async function GET(req: NextRequest, { params }: { params: { path: string[] } }) {
  const path = '/' + params.path.join('/') + (req.nextUrl.search ?? '');
  const data = await judgeApi(path);
  return Response.json(data);
}

export async function POST(req: NextRequest, { params }: { params: { path: string[] } }) {
  const path = '/' + params.path.join('/');
  const body = await req.text();
  const data = await judgeApi(path, { method: 'POST', body });
  return Response.json(data);
}

export async function PUT(req: NextRequest, { params }: { params: { path: string[] } }) {
  const path = '/' + params.path.join('/');
  const body = await req.text();
  const data = await judgeApi(path, { method: 'PUT', body });
  return Response.json(data);
}

export async function DELETE(req: NextRequest, { params }: { params: { path: string[] } }) {
  const path = '/' + params.path.join('/');
  await judgeApi(path, { method: 'DELETE' });
  return new Response(null, { status: 204 });
}
```

Sau đó trong FE React gọi `/api/judge/v1/problems` thay vì trực tiếp judge server.

---

## 4. Các endpoints chính

### 4.1 Problems

```http
# Danh sách problems (có filter)
GET /api/v1/problems?q=&tags=dp,greedy&difficulty=medium&page=0&size=20

# Response
{
  "content": [{
    "id": 1,
    "slug": "a-plus-b",
    "title": "A + B",
    "description": "...",
    "descriptionFormat": "MARKDOWN",   // hoặc "HTML"
    "timeLimitMs": 1000,
    "memoryLimitKb": 262144,
    "difficulty": "easy",              // "easy" | "medium" | "hard" | null
    "tags": ["math"],
    "published": true,
    "solvedCount": 42,
    "acceptanceRate": 78.5,
    "checkerType": "EXACT"             // "EXACT" | "CUSTOM"
  }],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20
}

# Chi tiết problem
GET /api/v1/problems/{slug}

# [Admin] Tạo problem
POST /api/v1/admin/problems
{
  "slug": "a-plus-b",
  "title": "A + B Problem",
  "description": "## Yêu cầu\nTính tổng **a + b**.",
  "descriptionFormat": "MARKDOWN",
  "timeLimitMs": 1000,
  "memoryLimitKb": 262144,
  "difficulty": "easy",
  "tags": ["math", "basic"]
}

# [Admin] Publish
POST /api/v1/admin/problems/{id}/publish

# [Admin] Upload test case
POST /api/v1/admin/problems/{id}/test-cases   (multipart/form-data)
  input:    <file.in>
  output:   <file.out>
  score:    1
  isSample: false
```

### 4.2 Submissions

```http
# Submit bài
POST /api/v1/submissions
{
  "problemId": 1,
  "language": "cpp",          // "cpp" | "java" | "python"
  "sourceCode": "#include...",
  "userRef": "alice",         // tùy chọn — dùng cho leaderboard
  "callbackUrl": "https://...",  // tùy chọn — webhook khi xong
  "contestId": 3              // tùy chọn — nếu submit trong contest
}

# Response (submission tạo, đang hàng đợi)
{
  "submissionId": "sub_abc123",
  "status": "PENDING",
  "language": "cpp",
  "createdAt": "2026-04-21T10:00:00"
}

# Poll kết quả
GET /api/v1/submissions/{submissionId}

# Response khi xong
{
  "submissionId": "sub_abc123",
  "status": "AC",             // AC | WA | TLE | MLE | RE | CE | SE
  "score": 100,
  "timeMs": 45,
  "language": "cpp",
  "errorMessage": null,
  "testResults": [
    { "testCaseId": 1, "status": "AC", "timeMs": 45, "memoryKb": 2048 }
  ],
  "sourceCode": "...",
  "createdAt": "...",
  "finishedAt": "..."
}

# Test run (không lưu vào DB, chạy trên sample test cases)
POST /api/v1/submissions/test
{
  "problemId": 1,
  "language": "python",
  "sourceCode": "a,b=map(int,input().split());print(a+b)"
}
```

**Verdict codes:**

| Code | Nghĩa |
|------|-------|
| `AC` | Accepted |
| `WA` | Wrong Answer |
| `TLE` | Time Limit Exceeded |
| `MLE` | Memory Limit Exceeded |
| `RE` | Runtime Error |
| `CE` | Compile Error |
| `SE` | System Error (Docker/infra) |
| `PENDING` | Đang chờ trong queue |
| `JUDGING` | Đang chấm |

### 4.3 Contests

```http
# Danh sách contests (public)
GET /api/v1/contests

# Chi tiết contest
GET /api/v1/contests/{slug}

# Đăng ký tham gia
POST /api/v1/contests/{slug}/register?userRef=alice

# Bảng xếp hạng contest
GET /api/v1/contests/{slug}/scoreboard

# [Admin] Tạo contest
POST /api/v1/admin/contests
{
  "slug": "icpc-2026",
  "title": "ICPC 2026 Regional",
  "description": "...",
  "startTime": "2026-05-01T08:00:00",
  "endTime": "2026-05-01T13:00:00",
  "isPublic": true
}

# [Admin] Thêm problem vào contest
POST /api/v1/admin/contests/{id}/problems
{
  "problemId": 11,
  "alias": "A",
  "orderIndex": 0
}

# [Admin] Gỡ problem
DELETE /api/v1/admin/contests/{id}/problems/{problemId}
```

### 4.4 Leaderboard & User Stats

```http
# Leaderboard toàn cục (public, cache 5 phút)
GET /api/v1/leaderboard?limit=50&offset=0

# Stats của một user
GET /api/v1/users/{userRef}/stats

# Response stats
{
  "userRef": "alice",
  "solvedProblems": 12,
  "totalSubmissions": 30,
  "acceptanceRate": 40.0,
  "languageBreakdown": { "cpp": 20, "python": 10 },
  "recentSubmissions": [...]
}
```

### 4.5 API Keys (Admin)

```http
# Tạo API key
POST /api/v1/admin/api-keys
{
  "clientName": "nextjs-admin",
  "rateLimitPerHour": 500,
  "isAdmin": true
}

# Kích hoạt / vô hiệu hóa
PATCH /api/v1/admin/api-keys/{id}/activate
PATCH /api/v1/admin/api-keys/{id}/deactivate
```

---

## 5. WebSocket — Realtime verdict

Dùng STOMP over SockJS. **Không cần API key** để kết nối WebSocket.

### Browser (SockJS + STOMP)

```js
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

function subscribeSubmission(submissionId, onUpdate) {
  const client = new Client({
    webSocketFactory: () => new SockJS('http://<server>:8433/ws'),
    onConnect: () => {
      client.subscribe(`/topic/submissions/${submissionId}`, (frame) => {
        const update = JSON.parse(frame.body);
        onUpdate(update);
        // update.status === "JUDGING"  → partial update, update.testResults có kết quả từng TC
        // update.status === "AC"|...   → final verdict, đóng subscription
      });
    },
  });
  client.activate();
  return () => client.deactivate();
}
```

### Message format

```json
// Partial (sau mỗi test case)
{
  "submissionId": "sub_abc123",
  "status": "JUDGING",
  "testResults": [
    { "testCaseId": 1, "status": "AC", "timeMs": 45, "memoryKb": 2048 }
  ]
}

// Final
{
  "submissionId": "sub_abc123",
  "status": "AC",
  "score": 100,
  "timeMs": 45,
  "testResults": [...],
  "errorMessage": null
}
```

**Pattern khuyến nghị cho Next.js**: mở WebSocket từ browser (không qua proxy vì là read-only, không cần key), với fallback polling REST sau 3 giây nếu WS thất bại.

---

## 6. Import problem từ ZIP

```http
POST /api/v1/admin/problems/import   (multipart/form-data)
  file: <archive.zip>
```

Cấu trúc ZIP:

```
problem.yml          ← bắt buộc
tests/
  1.in / 1.out
  sample_1.in / sample_1.out   ← "sample" hoặc "ex" prefix → is_sample=true
subtasks.yml         ← tùy chọn
checker.cpp          ← tùy chọn (custom judge)
```

`problem.yml` tối thiểu:

```yaml
slug: a-plus-b
title: "A + B Problem"
timeLimitMs: 1000
memoryLimitKb: 262144
description: "Tính tổng a + b"
description_format: MARKDOWN     # hoặc HTML (mặc định: MARKDOWN)
difficulty: easy
tags:
  - math
```

---

## 7. Ngôn ngữ lập trình hỗ trợ

| `language` | Compiler/Runtime | Source file |
|------------|-----------------|-------------|
| `cpp` | GCC 13, `-O2 -std=c++17` | `solution.cpp` |
| `java` | OpenJDK 21 | `Solution.java` |
| `python` | Python 3.12 | `solution.py` |

Thêm ngôn ngữ mới: chỉ cần thêm entry vào `judge.languages` trong `application.yml`, không cần sửa code.

---

## 8. Environment variables cần thiết

Khai báo trong `.env.local` (Next.js) hoặc `.env` (Express):

```env
# URL nội bộ của judge server (chỉ dùng server-side)
JUDGE_BASE_URL=http://localhost:8433

# API key admin (lấy từ admin.html hoặc DB)
JUDGE_API_KEY=sk_xxxxxxxxxxxxxxxx

# (tùy chọn) Public URL cho WebSocket từ browser
NEXT_PUBLIC_JUDGE_WS_URL=http://localhost:8433
```

---

## 9. Luồng tích hợp phổ biến

### Luồng submit + realtime từ Next.js

```
Browser                     Next.js API route             Judge Server
   │                               │                            │
   │  POST /api/judge/submit       │                            │
   │──────────────────────────────>│                            │
   │                               │  POST /api/v1/submissions  │
   │                               │   X-API-Key: <key>         │
   │                               │──────────────────────────>│
   │                               │   { submissionId: "..." }  │
   │                               │<──────────────────────────│
   │   { submissionId: "..." }     │                            │
   │<──────────────────────────────│                            │
   │                               │                            │
   │  WS connect /ws (direct)      │                            │
   │──────────────────────────────────────────────────────────>│
   │  subscribe /topic/submissions/{id}                         │
   │<─────────────────── partial updates ──────────────────────│
   │<─────────────────── final verdict  ───────────────────────│
```

### Luồng tạo contest và quản lý problems (Next.js Admin)

```
Admin Browser              Next.js API route            Judge Server
   │                              │                           │
   │  POST /api/judge/admin/contests                          │
   │─────────────────────────────>│                           │
   │                              │  POST /api/v1/admin/...   │
   │                              │   X-API-Key: <admin_key>  │
   │                              │─────────────────────────>│
   │  contest created             │<─────────────────────────│
   │<─────────────────────────────│                           │
   │                              │                           │
   │  POST /api/judge/admin/contests/{id}/problems            │
   │  { problemId, alias, orderIndex }                        │
   │─────────────────────────────>│                           │
   │                              │──────────────────────────>│
   │  problem added               │<──────────────────────────│
   │<─────────────────────────────│                           │
```

---

## 10. Health check

```http
GET /actuator/health

# Response
{
  "status": "UP",
  "components": {
    "db":         { "status": "UP" },
    "redis":      { "status": "UP" },
    "docker":     { "status": "UP" },
    "diskSpace":  { "status": "UP" }
  }
}
```

Nếu `docker.status = "DOWN"`, mọi submission sẽ trả về `SE`. Kiểm tra Docker daemon trên host trước khi debug logic.

---

## 11. Rate limiting

Mỗi API key có `rate_limit_per_hour`. Khi vượt giới hạn:

```
HTTP 429 Too Many Requests
{ "error": "RATE_LIMIT_EXCEEDED", "message": "Rate limit exceeded" }
```

Đặt giá trị phù hợp khi tạo key cho từng client. Admin key nên có limit cao (≥ 5000/hr).

---

## 12. Ví dụ tham khảo trong repo

```
examples/
├── curl/
│   └── quickstart.sh            — 5-step curl script: create → test → submit
└── reactjs/
    ├── JudgeWidget.jsx           — React widget nộp bài + realtime verdict
    └── admin/
        ├── server.js             — Express proxy (API key nằm ở đây)
        ├── AdminProblems.jsx     — CRUD problems qua proxy
        └── AdminContests.jsx     — CRUD contests qua proxy
```
