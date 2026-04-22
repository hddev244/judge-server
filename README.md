# Judge Server

**Judge as a Service** — hệ thống chấm bài lập trình tự động hỗ trợ C++, Java, Python. Mỗi submission chạy trong Docker sandbox cô lập, trả verdict realtime qua WebSocket.

## Tính năng

- **Chấm bài tự động** — C++ (GCC 13), Java 21, Python 3.12 (cấu hình qua YAML/env)
- **Docker sandbox** — `--network none`, memory/CPU limit, read-only filesystem
- **WebSocket realtime** — STOMP over SockJS, push verdict sau mỗi test case, không cần polling
- **Test Run** — chạy test mẫu đồng bộ, không lưu DB
- **Queue** — Redis BRPOP, số worker cấu hình được, per-submission lock chống race condition
- **Subtask scoring** — nhóm test case theo subtask, chấm all-or-nothing
- **Custom checker** — special judge: compile checker C++/Java/Python, chạy với `<input> <expected> <actual>`
- **Import problem từ ZIP** — `problem.yml` + `tests/*.in/*.out` + `subtasks.yml` + `checker.cpp`
- **Problem tags & difficulty** — gán tags (dp, greedy, ...) và độ khó (easy/medium/hard), tìm kiếm/lọc
- **Topics & Categories** — phân loại bài theo chủ đề và dạng đề, lọc kết hợp trong search
- **Contest mode** — tạo contest, đăng ký thí sinh, submit theo contest, bảng điểm với penalty
- **Leaderboard** — xếp hạng global theo số bài solved, cache 5 phút
- **User stats** — profile từng user: bài đã giải, tỷ lệ AC, ngôn ngữ dùng, submission gần đây
- **Webhook** — callback khi chấm xong, auto-retry
- **Rate limiting** — Bucket4j, per API key
- **Admin Panel** — quản lý problem, subtask, checker, test case, contest, API keys

## Stack

| Thành phần | Công nghệ |
|-----------|-----------|
| API | Spring Boot 3.2, Java 21 (virtual threads) |
| Database | PostgreSQL 16 + Flyway |
| Queue | Redis 7 BRPOP |
| Realtime | WebSocket (STOMP over SockJS) |
| Cache | Caffeine (in-process, 5 min TTL) |
| Sandbox | Docker-in-Docker (socket mount) |
| Rate limit | Bucket4j |
| Build | Maven, multi-stage Dockerfile |

## Khởi chạy nhanh

**Yêu cầu:** Docker, Docker Compose

```bash
git clone https://github.com/hddev244/judge-server.git
cd judge-server

# Pull sandbox images (lần đầu)
bash scripts/init-images.sh

# Tạo thư mục dùng chung với Docker daemon
sudo mkdir -p /tmp/judge /data/problems
sudo chmod 777 /tmp/judge /data/problems

# Start
docker compose up -d

# Health check
curl http://localhost:8080/actuator/health
```

Lần đầu khởi động, tạo API key admin:

```bash
docker compose exec postgres psql -U judge judgedb -c \
  "INSERT INTO api_keys(key,client_name,is_active,is_admin,rate_limit_per_hour) \
   VALUES ('sk_admin_local','admin',true,true,9999);"
```

## Cấu hình ngôn ngữ

```yaml
judge:
  languages:
    cpp:
      image: gcc:13
      source-file: solution.cpp
      compile-cmd: "g++ -O2 -std=c++17 -o solution solution.cpp"
      run-cmd: "./solution"
    java:
      image: eclipse-temurin:21
      source-file: Solution.java
      compile-cmd: "javac -encoding UTF-8 Solution.java"
      run-cmd: "java -cp /code -Xmx{mem}m Solution"
    python:
      image: python:3.12-slim
      source-file: solution.py
      compile-cmd: ""
      run-cmd: "python3 solution.py"
```

Thêm ngôn ngữ mới: thêm entry vào `judge.languages`, không cần sửa code.

## Import Problem từ ZIP

```
my-problem/
├── problem.yml          ← thông tin bài (bắt buộc)
├── tests/
│   ├── 1.in / 1.out
│   └── sample1.in / sample1.out   ← tên bắt đầu "sample"/"ex" → test mẫu
├── subtasks.yml         ← nhóm test theo subtask (không bắt buộc)
└── checker.cpp          ← custom checker (không bắt buộc)
```

**`problem.yml`:**

```yaml
slug: a-plus-b
title: "A + B Problem"
description: "Cho hai số nguyên a và b. In ra tổng a + b."
description_format: MARKDOWN   # MARKDOWN (mặc định) hoặc HTML
timeLimitMs: 1000
memoryLimitKb: 262144
difficulty: easy                # easy | medium | hard (không bắt buộc)
tags: [math, implementation]    # danh sách tags (không bắt buộc)
```

**`subtasks.yml`:**

```yaml
- name: "Subtask 1 — a, b ≤ 100"
  score: 30
  tests: ["1", "2"]

- name: "Subtask 2 — a, b ≤ 10^9"
  score: 70
  tests: ["3", "4", "5"]
```

**Import:**

```bash
cd my-problem && zip -r ../my-problem.zip .

curl -X POST http://localhost:8080/api/v1/admin/problems/import \
  -H "X-API-Key: YOUR_ADMIN_KEY" \
  -F "file=@my-problem.zip"
```

> Giới hạn mặc định: **50 MB** per file, 52 MB per request. Điều chỉnh qua `spring.servlet.multipart.max-file-size` trong `application.yml`.

## Contest Mode

```bash
# Tạo contest
curl -X POST http://localhost:8080/api/v1/admin/contests \
  -H "X-API-Key: ADMIN_KEY" -H "Content-Type: application/json" \
  -d '{"slug":"round-1","title":"Round 1","startTime":"2026-05-01T09:00:00","endTime":"2026-05-01T12:00:00","isPublic":true}'

# Thêm problem vào contest
curl -X POST http://localhost:8080/api/v1/admin/contests/{id}/problems \
  -H "X-API-Key: ADMIN_KEY" -H "Content-Type: application/json" \
  -d '{"problemId":1,"alias":"A","orderIndex":1}'

# Đăng ký thí sinh
curl -X POST "http://localhost:8080/api/v1/contests/round-1/register?userRef=alice"

# Submit theo contest
curl -X POST http://localhost:8080/api/v1/submissions \
  -H "X-API-Key: YOUR_KEY" -H "Content-Type: application/json" \
  -d '{"problemId":1,"language":"cpp","sourceCode":"...","userRef":"alice","contestId":1}'

# Xem bảng điểm
curl http://localhost:8080/api/v1/contests/round-1/scoreboard
```

**Scoreboard:** sắp xếp theo `totalScore DESC`, `totalPenalty ASC`.  
Penalty = số phút kể từ đầu contest + 20 phút × số lần WA/TLE/MLE/RE.

## WebSocket Realtime

Client subscribe vào `/topic/submissions/{id}` sau khi submit để nhận verdict realtime:

```javascript
const stompClient = Stomp.over(new SockJS('/ws'));
stompClient.connect({}, () => {
  stompClient.subscribe(`/topic/submissions/${submissionId}`, (msg) => {
    const { status, score, timeMs, testResults } = JSON.parse(msg.body);
    // status = "JUDGING" (partial) hoặc verdict cuối: "AC"/"WA"/"TLE"/...
    if (!['PENDING','JUDGING'].includes(status)) stompClient.disconnect();
  });
});
```

REST endpoint `GET /api/v1/submissions/{id}` vẫn hoạt động bình thường (backward compatible).

## Problem Search

```bash
# Tất cả params đều optional, có thể kết hợp tùy ý
GET /api/v1/problems?q=tổng&tags=dp,greedy&difficulty=medium&topicSlug=dp&categorySlug=graph-theory&page=0&size=20
```

| Param | Ví dụ | Mô tả |
|-------|-------|-------|
| `q` | `tổng` | Tìm theo title (case-insensitive) |
| `tags` | `dp,greedy` | Tags — AND semantics |
| `difficulty` | `easy` | `easy` / `medium` / `hard` |
| `topicSlug` | `dp` | Slug của topic |
| `categorySlug` | `graph-theory` | Slug của category |
| `page` | `0` | Trang (bắt đầu từ 0) |
| `size` | `20` | Số bài/trang (tối đa 100) |

```json
{
  "content": [{
    "id": 1, "slug": "a-plus-b", "title": "A + B",
    "difficulty": "easy", "tags": ["math"],
    "topics": [{ "id": 1, "name": "Dynamic Programming", "slug": "dp" }],
    "categories": [{ "id": 1, "name": "Graph Theory", "slug": "graph-theory" }],
    "solvedCount": 42, "acceptanceRate": 78.5
  }],
  "totalElements": 42, "page": 0, "size": 20
}
```

`tags=dp,greedy` trả về bài có **đồng thời** cả tag `dp` lẫn `greedy`.

## Topics & Categories

```bash
# [Public] Danh sách topics
GET /api/v1/topics
GET /api/v1/topics/{slug}

# [Public] Danh sách categories
GET /api/v1/categories
GET /api/v1/categories/{slug}

# [Admin] Tạo topic
POST /api/v1/admin/topics
{ "name": "Dynamic Programming", "slug": "dp", "description": "..." }

# [Admin] Thêm problems vào topic
POST /api/v1/admin/topics/{id}/problems
{ "problemIds": [1, 2, 3] }

# [Admin] Tương tự với categories
POST /api/v1/admin/categories
POST /api/v1/admin/categories/{id}/problems
```

Khi tạo/cập nhật problem có thể gán đồng thời:

```json
{
  "slug": "a-plus-b",
  "topicIds": [1, 2],
  "categoryIds": [1]
}
```

---

## Kết nối từ Backend Server khác

Đây là cách tích hợp Judge Server vào **server backend A** (Node.js, Python, Spring, Go, ...) để gửi bài chấm và nhận kết quả.

### Bước 1 — Tạo API Key

```bash
# Qua admin.html → API Keys → Tạo API Key
# Hoặc trực tiếp qua API (cần admin key)
curl -X POST http://<judge-host>:8433/api/v1/admin/api-keys \
  -H "X-API-Key: <admin_key>" \
  -H "Content-Type: application/json" \
  -d '{"clientName": "backend-a", "rateLimitPerHour": 1000, "isAdmin": false}'
# → nhận { "key": "sk_xxxxxxxx" }
```

Lưu key vào biến môi trường server-side, **không commit lên git**, **không trả về browser**.

### Bước 2 — Gửi bài chấm

```bash
curl -X POST http://<judge-host>:8433/api/v1/submissions \
  -H "X-API-Key: sk_xxxxxxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "problemId": 1,
    "language": "cpp",
    "sourceCode": "#include<bits/stdc++.h>\nint main(){int a,b;cin>>a>>b;cout<<a+b;}",
    "userRef": "user-123",
    "callbackUrl": "https://backend-a.example.com/judge/callback"
  }'
# → { "submissionId": "sub_abc123", "status": "PENDING" }
```

### Bước 3 — Nhận kết quả

**Cách A — Webhook (khuyến nghị cho server-to-server)**

Khi chấm xong, judge server gọi `callbackUrl` của bạn:

```json
POST https://backend-a.example.com/judge/callback
{
  "submissionId": "sub_abc123",
  "status": "AC",
  "score": 100,
  "timeMs": 45,
  "testResults": [
    { "testCaseId": 1, "status": "AC", "timeMs": 45, "memoryKb": 2048 }
  ],
  "errorMessage": null
}
```

Judge server tự retry nếu callback thất bại (HTTP ≠ 2xx), với backoff tăng dần.

**Cách B — Polling**

```bash
# Poll đến khi status không còn là PENDING/JUDGING
curl http://<judge-host>:8433/api/v1/submissions/sub_abc123 \
  -H "X-API-Key: sk_xxxxxxxx"
```

**Cách C — WebSocket (từ browser, không cần key)**

```js
// Browser kết nối trực tiếp đến judge server, không qua proxy
const client = new Client({
  webSocketFactory: () => new SockJS('http://<judge-host>:8433/ws'),
  onConnect: () => {
    client.subscribe('/topic/submissions/sub_abc123', (msg) => {
      const { status, score, testResults } = JSON.parse(msg.body);
      if (!['PENDING','JUDGING'].includes(status)) client.deactivate();
    });
  },
});
client.activate();
```

### Ví dụ Node.js / Express

```js
// judgeClient.js
const JUDGE_BASE = process.env.JUDGE_BASE_URL; // http://<host>:8433
const JUDGE_KEY  = process.env.JUDGE_API_KEY;

async function submit({ problemId, language, sourceCode, userRef, callbackUrl }) {
  const res = await fetch(`${JUDGE_BASE}/api/v1/submissions`, {
    method: 'POST',
    headers: { 'X-API-Key': JUDGE_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({ problemId, language, sourceCode, userRef, callbackUrl }),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json(); // { submissionId, status }
}

async function getResult(submissionId) {
  const res = await fetch(`${JUDGE_BASE}/api/v1/submissions/${submissionId}`, {
    headers: { 'X-API-Key': JUDGE_KEY },
  });
  return res.json();
}

module.exports = { submit, getResult };
```

```js
// router.js — nhận callback từ judge
app.post('/judge/callback', express.json(), (req, res) => {
  const { submissionId, status, score, testResults } = req.body;
  // Lưu kết quả vào DB của backend A, thông báo cho user...
  res.sendStatus(200); // phải trả 2xx để judge server không retry
});
```

### Ví dụ Python

```python
import os, requests

JUDGE_BASE = os.environ['JUDGE_BASE_URL']
JUDGE_KEY  = os.environ['JUDGE_API_KEY']
HEADERS    = {'X-API-Key': JUDGE_KEY, 'Content-Type': 'application/json'}

def submit(problem_id, language, source_code, user_ref=None, callback_url=None):
    r = requests.post(f'{JUDGE_BASE}/api/v1/submissions', headers=HEADERS, json={
        'problemId': problem_id, 'language': language, 'sourceCode': source_code,
        'userRef': user_ref, 'callbackUrl': callback_url,
    })
    r.raise_for_status()
    return r.json()  # {'submissionId': 'sub_...', 'status': 'PENDING'}

def get_result(submission_id):
    r = requests.get(f'{JUDGE_BASE}/api/v1/submissions/{submission_id}', headers=HEADERS)
    r.raise_for_status()
    return r.json()
```

### Luồng server-to-server đầy đủ

```
Backend A                        Judge Server
    │                                │
    │  POST /api/v1/submissions      │
    │  X-API-Key: sk_xxx             │
    │  { problemId, lang, code,      │
    │    callbackUrl: /callback }    │
    │───────────────────────────────>│
    │  { submissionId: "sub_abc" }   │
    │<───────────────────────────────│
    │                                │  [async judge in Docker sandbox]
    │                                │
    │  POST /callback                │
    │  { status: "AC", score: 100 }  │
    │<───────────────────────────────│
    │  200 OK                        │
    │───────────────────────────────>│
```

### Các endpoint Backend A hay dùng

| Mục đích | Method | Endpoint |
|----------|--------|----------|
| Gửi bài chấm | `POST` | `/api/v1/submissions` |
| Lấy kết quả | `GET` | `/api/v1/submissions/{id}` |
| Lọc submissions | `GET` | `/api/v1/submissions?problemSlug=&userRef=&status=&page=&size=` |
| Test run (không lưu) | `POST` | `/api/v1/submissions/test` |
| Danh sách bài | `GET` | `/api/v1/problems?topicSlug=&categorySlug=&difficulty=&tags=&page=&size=` |
| Chi tiết bài | `GET` | `/api/v1/problems/{slug}` |
| Danh sách topics | `GET` | `/api/v1/topics` |
| Danh sách categories | `GET` | `/api/v1/categories` |
| User stats | `GET` | `/api/v1/users/{userRef}/stats` |
| Leaderboard | `GET` | `/api/v1/leaderboard?limit=&offset=` |
| Health check | `GET` | `/actuator/health` |

> Xem đầy đủ tại [`AI_AGENTS.md`](./AI_AGENTS.md).

---

## Leaderboard & User Stats

```bash
# Leaderboard global (public, không cần API key)
GET /api/v1/leaderboard?limit=50&offset=0

# Profile người dùng
GET /api/v1/users/{userRef}/stats
```

## API nhanh

Hầu hết endpoints cần header `X-API-Key`. Các endpoint **public** (không cần key):
`GET /api/v1/leaderboard`, `GET /api/v1/users/*/stats`, `GET /api/v1/contests`, `GET /api/v1/contests/{slug}`, `POST /api/v1/contests/{slug}/register`, `GET /api/v1/contests/{slug}/scoreboard`.

```bash
# Test thử (đồng bộ, không lưu DB)
curl -X POST http://localhost:8080/api/v1/submissions/test \
  -H "X-API-Key: YOUR_KEY" -H "Content-Type: application/json" \
  -d '{"problemId":1,"language":"cpp","sourceCode":"..."}'

# Nộp bài (async, realtime qua WebSocket)
curl -X POST http://localhost:8080/api/v1/submissions \
  -H "X-API-Key: YOUR_KEY" -H "Content-Type: application/json" \
  -d '{"problemId":1,"language":"cpp","sourceCode":"...","userRef":"alice"}'

# Xem kết quả
curl http://localhost:8080/api/v1/submissions/sub_xxxxxxxxxx \
  -H "X-API-Key: YOUR_KEY"
```

Verdicts: `AC` · `WA` · `TLE` · `MLE` · `RE` · `CE` · `SE` · `PENDING` · `JUDGING`

## Giao diện Web

| URL | Mô tả |
|-----|-------|
| `/admin.html` | Admin Panel (problem, contest, checker, subtask, test case, API keys) |
| `/solve.html` | Trang làm bài (Test Run + Submit + WebSocket verdict) |
| `/docs.html` | API Documentation |

## Subtask Scoring

Test case thuộc subtask: subtask chỉ được điểm khi **tất cả** test case trong subtask đều AC.  
Test case không thuộc subtask nào: tính điểm riêng từng case.

## Custom Checker

```cpp
// checker.cpp — nhận 3 args: input expected actual; exit 0 = AC
#include <bits/stdc++.h>
using namespace std;
int main(int argc, char* argv[]) {
    ifstream expected(argv[2]), actual(argv[3]);
    double a, b;
    expected >> a; actual >> b;
    return abs(a - b) < 1e-6 ? 0 : 1;
}
```

## Biến môi trường

| Biến | Mặc định | Mô tả |
|------|----------|-------|
| `DB_URL` | — | PostgreSQL JDBC URL |
| `DB_USERNAME` | — | PostgreSQL username |
| `DB_PASSWORD` | — | PostgreSQL password |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `JUDGE_WORKERS` | `4` | Số worker thread |
| `JUDGE_WORK_BASE` | `/tmp/judge` | Thư mục tạm compile/run |
| `JUDGE_TESTCASE_BASE_PATH` | `/data/problems` | Lưu file test case |
| `JUDGE_QUEUE_KEY` | `judge:queue` | Redis list key |
| `SERVER_PORT` | `8080` | HTTP port |
| `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` | `50MB` | Giới hạn kích thước file upload (ZIP import) |
| `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` | `52MB` | Giới hạn kích thước request multipart |

## License

MIT
