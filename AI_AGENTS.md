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
| Public | Không cần key | `/api/v1/leaderboard`, `/api/v1/contests`, `/api/v1/topics`, `/api/v1/categories`, WebSocket |
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
# Danh sách problems (tất cả params optional, kết hợp tùy ý)
GET /api/v1/problems?q=tổng&tags=dp,greedy&difficulty=medium&topicSlug=dp&categorySlug=graph-theory&page=0&size=20

# Response
{
  "content": [{
    "id": 1,
    "slug": "a-plus-b",
    "title": "A + B",
    "description": "...",
    "descriptionFormat": "MARKDOWN",
    "timeLimitMs": 1000,
    "memoryLimitKb": 262144,
    "difficulty": "easy",
    "tags": ["math"],
    "topics": [{ "id": 1, "name": "Dynamic Programming", "slug": "dp" }],
    "categories": [{ "id": 1, "name": "Graph Theory", "slug": "graph-theory" }],
    "isPublished": true,
    "solvedCount": 42,
    "acceptanceRate": 78.5,
    "checkerType": "EXACT"
  }],
  "totalElements": 100,
  "page": 0,
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
  "tags": ["math", "basic"],
  "topicIds": [1, 2],       // optional — gán vào topics
  "categoryIds": [1]        // optional — gán vào categories
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

### 4.5 Topics & Categories

```http
# [Public] Danh sách topics (không cần API key)
GET /api/v1/topics
GET /api/v1/topics/{slug}

# [Public] Danh sách categories
GET /api/v1/categories
GET /api/v1/categories/{slug}

# Response
[{
  "id": 1,
  "name": "Dynamic Programming",
  "slug": "dp",
  "description": "...",
  "problemCount": 5,
  "problems": [{ "id": 1, "slug": "a-plus-b", "title": "A + B", "difficulty": "easy" }],
  "createdAt": "2026-04-21T14:16:16"
}]

# [Admin] Tạo topic / category
POST /api/v1/admin/topics
POST /api/v1/admin/categories
{ "name": "Dynamic Programming", "slug": "dp", "description": "..." }

# [Admin] Sửa
PUT /api/v1/admin/topics/{id}
PUT /api/v1/admin/categories/{id}

# [Admin] Xóa
DELETE /api/v1/admin/topics/{id}
DELETE /api/v1/admin/categories/{id}

# [Admin] Thêm problems vào topic/category
POST /api/v1/admin/topics/{id}/problems
POST /api/v1/admin/categories/{id}/problems
{ "problemIds": [1, 2, 3] }

# [Admin] Gỡ problem khỏi topic/category
DELETE /api/v1/admin/topics/{id}/problems/{problemId}
DELETE /api/v1/admin/categories/{id}/problems/{problemId}
```

### 4.6 API Keys (Admin)

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

## 12. Kết nối từ Backend Server khác (Server-to-Server)

Dành cho các hệ thống muốn dùng Judge Server như một **dịch vụ chấm bài từ xa** — backend A gửi submission, nhận verdict qua webhook hoặc polling.

### Luồng tổng quát

```
Backend A                        Judge Server
    │                                │
    │  1. POST /api/v1/submissions   │
    │     X-API-Key: sk_xxx          │
    │     { problemId, lang, code,   │
    │       callbackUrl }            │
    │───────────────────────────────>│
    │  { submissionId: "sub_abc" }   │
    │<───────────────────────────────│
    │                                │  [async: compile + Docker sandbox]
    │  2. POST <callbackUrl>         │
    │  { status:"AC", score:100, … } │
    │<───────────────────────────────│
    │  200 OK (không retry nếu 2xx)  │
    │───────────────────────────────>│
```

### Setup

**1. Tạo API key cho backend A:**

```bash
curl -X POST http://<judge-host>:8433/api/v1/admin/api-keys \
  -H "X-API-Key: <admin_key>" -H "Content-Type: application/json" \
  -d '{"clientName":"backend-a","rateLimitPerHour":2000,"isAdmin":false}'
# Nhận: { "key": "sk_xxxxxxxxx" }
```

Lưu key vào biến môi trường, không bao giờ để trong source code hay trả về client.

**2. Gửi submission:**

```bash
curl -X POST http://<judge-host>:8433/api/v1/submissions \
  -H "X-API-Key: $JUDGE_API_KEY" -H "Content-Type: application/json" \
  -d '{
    "problemId": 1,
    "language": "cpp",
    "sourceCode": "...",
    "userRef": "user-uuid-123",
    "callbackUrl": "https://your-backend.com/api/judge-callback"
  }'
```

**3. Nhận callback:**

```
POST https://your-backend.com/api/judge-callback
Content-Type: application/json

{
  "submissionId": "sub_abc123",
  "status": "AC",          // AC|WA|TLE|MLE|RE|CE|SE
  "score": 100,
  "timeMs": 45,
  "userRef": "user-uuid-123",
  "testResults": [
    { "testCaseId": 1, "status": "AC", "timeMs": 45, "memoryKb": 2048 }
  ],
  "errorMessage": null,
  "finishedAt": "2026-04-22T09:00:00"
}
```

> Callback endpoint **phải trả HTTP 2xx**. Nếu không, judge server retry tự động với backoff.

### Ví dụ theo ngôn ngữ

**Node.js**

```js
// .env: JUDGE_BASE_URL=http://...:8433  JUDGE_API_KEY=sk_xxx
const headers = {
  'X-API-Key': process.env.JUDGE_API_KEY,
  'Content-Type': 'application/json',
};

async function submit(problemId, language, sourceCode, userRef) {
  const res = await fetch(`${process.env.JUDGE_BASE_URL}/api/v1/submissions`, {
    method: 'POST', headers,
    body: JSON.stringify({
      problemId, language, sourceCode, userRef,
      callbackUrl: `${process.env.APP_URL}/api/judge-callback`,
    }),
  });
  return res.json(); // { submissionId, status: "PENDING" }
}

// Express webhook handler
app.post('/api/judge-callback', express.json(), async (req, res) => {
  const { submissionId, status, score, userRef, testResults } = req.body;
  await db.submissions.update({ submissionId }, { status, score, testResults });
  // notify user via WebSocket, email, etc.
  res.sendStatus(200); // phải trả 2xx
});
```

**Python (FastAPI)**

```python
import os, httpx

JUDGE_BASE = os.environ['JUDGE_BASE_URL']
JUDGE_KEY  = os.environ['JUDGE_API_KEY']
HEADERS    = {'X-API-Key': JUDGE_KEY, 'Content-Type': 'application/json'}

async def submit(problem_id: int, language: str, source_code: str, user_ref: str):
    async with httpx.AsyncClient() as client:
        r = await client.post(f'{JUDGE_BASE}/api/v1/submissions', headers=HEADERS, json={
            'problemId': problem_id, 'language': language, 'sourceCode': source_code,
            'userRef': user_ref,
            'callbackUrl': f"{os.environ['APP_URL']}/api/judge-callback",
        })
        r.raise_for_status()
        return r.json()  # { submissionId, status }

@app.post('/api/judge-callback')
async def judge_callback(payload: dict):
    sub_id = payload['submissionId']
    status = payload['status']
    score  = payload.get('score', 0)
    # cập nhật DB, push notification cho user...
    return {'ok': True}  # 2xx để judge không retry
```

**Spring Boot (RestTemplate/WebClient)**

```java
@Service
public class JudgeClient {
    @Value("${judge.base-url}") private String baseUrl;
    @Value("${judge.api-key}")  private String apiKey;
    private final RestTemplate rest = new RestTemplate();

    public String submit(Long problemId, String language, String sourceCode, String userRef) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
            "problemId", problemId, "language", language,
            "sourceCode", sourceCode, "userRef", userRef,
            "callbackUrl", appUrl + "/api/judge-callback"
        );
        ResponseEntity<Map> res = rest.exchange(
            baseUrl + "/api/v1/submissions",
            HttpMethod.POST, new HttpEntity<>(body, headers), Map.class
        );
        return (String) res.getBody().get("submissionId");
    }
}

@RestController
public class JudgeCallbackController {
    @PostMapping("/api/judge-callback")
    public ResponseEntity<Void> callback(@RequestBody Map<String, Object> payload) {
        String subId  = (String) payload.get("submissionId");
        String status = (String) payload.get("status");
        // lưu DB, notify user...
        return ResponseEntity.ok().build(); // 2xx bắt buộc
    }
}
```

### Không có callbackUrl — dùng polling

```js
async function waitForResult(submissionId, intervalMs = 1000, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const r = await fetch(`${JUDGE_BASE}/api/v1/submissions/${submissionId}`, { headers });
    const data = await r.json();
    if (!['PENDING', 'JUDGING'].includes(data.status)) return data;
    await new Promise(resolve => setTimeout(resolve, intervalMs));
  }
  throw new Error('Timeout waiting for judge result');
}
```

### Lấy danh sách bài theo chủ đề (để hiển thị trong backend A)

```bash
# Tất cả params optional
GET /api/v1/problems?topicSlug=dp&categorySlug=graph-theory&difficulty=medium&page=0&size=20
# Không cần API key nếu endpoint được public — cần key nếu bạn config bảo vệ

# Danh sách topics
GET /api/v1/topics          # public, không cần key

# Danh sách categories
GET /api/v1/categories      # public, không cần key
```

---

## 13. Ví dụ tham khảo trong repo

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
