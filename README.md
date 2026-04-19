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
# Lọc theo tags (AND semantics), độ khó, từ khóa, phân trang
GET /api/v1/problems?tags=dp,greedy&difficulty=medium&q=sort&page=0&size=20

# Response:
{
  "content": [{ "id","slug","title","difficulty","tags","solvedCount","acceptanceRate",... }],
  "totalElements": 42,
  "page": 0,
  "size": 20
}
```

`tags=dp,greedy` trả về bài có **đồng thời** cả tag `dp` lẫn `greedy`.

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

## License

MIT
