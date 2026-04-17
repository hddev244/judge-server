# Judge Server

**Judge as a Service** — hệ thống chấm bài lập trình tự động hỗ trợ C++, Java, Python. Mỗi submission chạy trong Docker sandbox cô lập, trả verdict trong vài giây.

## Tính năng

- **Chấm bài tự động** — C++ (GCC 13), Java 21, Python 3.12 (cấu hình qua YAML/env)
- **Docker sandbox** — `--network none`, memory/CPU limit, read-only filesystem
- **Test Run** — chạy test mẫu đồng bộ, không lưu DB
- **Queue** — Redis BRPOP, số worker cấu hình được
- **Subtask scoring** — nhóm test case theo subtask, chấm all-or-nothing
- **Custom checker** — special judge: compile checker C++/Java/Python, chạy với `<input> <expected> <actual>`
- **Import problem từ ZIP** — `problem.yml` + `tests/*.in/*.out` + `subtasks.yml` + `checker.cpp`
- **Webhook** — callback khi chấm xong, auto-retry
- **Rate limiting** — Bucket4j, per API key
- **Admin Panel** — quản lý problem, subtask, checker, test case (upload ZIP batch), submissions, API keys

## Stack

| Thành phần | Công nghệ |
|-----------|-----------|
| API | Spring Boot 3.2, Java 21 (virtual threads) |
| Database | PostgreSQL 16 + Flyway |
| Queue | Redis 7 BRPOP |
| Sandbox | Docker-in-Docker (socket mount) |
| Rate limit | Bucket4j |
| Build | Maven, multi-stage Dockerfile |

## Khởi chạy nhanh

**Yêu cầu:** Docker, Docker Compose

```bash
git clone https://github.com/hddev244/judge-server.git
cd judge-server

# Copy cấu hình môi trường
cp .env.example .env
# Chỉnh sửa .env nếu cần (DB password, port, ...)

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
# Seed API key admin trực tiếp vào DB
docker compose exec postgres psql -U judge judgedb -c \
  "INSERT INTO api_keys(key,client_name,is_active,is_admin,rate_limit_per_hour) \
   VALUES ('sk_admin_local','admin',true,true,9999);"
```

## Cấu hình ngôn ngữ

Ngôn ngữ được cấu hình trong `application.yml` (hoặc qua biến môi trường):

```yaml
judge:
  languages:
    cpp:
      image: ${JUDGE_LANG_CPP_IMAGE:gcc:13}
      source-file: solution.cpp
      compile-cmd: "g++ -O2 -std=c++17 -o solution solution.cpp"
      run-cmd: "./solution"
    java:
      image: ${JUDGE_LANG_JAVA_IMAGE:eclipse-temurin:21}
      source-file: Solution.java
      compile-cmd: "javac -encoding UTF-8 Solution.java"
      run-cmd: "java -cp /code -Xmx{mem}m Solution"
    python:
      image: ${JUDGE_LANG_PYTHON_IMAGE:python:3.12-slim}
      source-file: solution.py
      compile-cmd: ""
      run-cmd: "python3 solution.py"
```

Thêm ngôn ngữ mới: thêm entry vào `judge.languages`, không cần sửa code.

## Import Problem từ ZIP

Thay vì tạo problem rồi upload từng file, bạn có thể đóng gói toàn bộ vào một file `.zip` rồi import một lần.

**Bước 1 — Tạo thư mục với cấu trúc sau:**

```
my-problem/
├── problem.yml        ← thông tin bài (bắt buộc)
├── tests/
│   ├── 1.in           ← input test 1
│   ├── 1.out          ← output mong đợi của test 1
│   ├── 2.in
│   └── 2.out
├── subtasks.yml       ← nhóm test theo subtask (không bắt buộc)
└── checker.cpp        ← custom checker (không bắt buộc)
```

> Tên file trong `tests/` tùy ý, miễn là `.in` và `.out` cùng tên gốc (vd: `sample1.in` / `sample1.out`).  
> File có tên bắt đầu bằng `sample` hoặc `ex` sẽ tự động được đánh dấu là **test mẫu** (hiển thị trong đề bài).

**Bước 2 — Viết `problem.yml`:**

```yaml
slug: a-plus-b          # ID duy nhất, dùng trong URL
title: "A + B Problem"
description: "Cho hai số nguyên a và b. In ra tổng a + b."
timeLimitMs: 1000       # giới hạn thời gian (ms)
memoryLimitKb: 262144   # giới hạn bộ nhớ (KB), 262144 = 256 MB
```

**Bước 3 — (Tùy chọn) Viết `subtasks.yml` nếu bài có subtask:**

```yaml
- name: "Subtask 1 — a, b ≤ 100"
  score: 30
  tests: ["1", "2"]      # tên file (không kèm đuôi .in/.out)

- name: "Subtask 2 — a, b ≤ 10^9"
  score: 70
  tests: ["3", "4", "5"]
```

**Bước 4 — Nén thành ZIP và import:**

```bash
cd my-problem && zip -r ../my-problem.zip .
```

Sau đó vào **Admin Panel → Problems → 📦 Import ZIP** và chọn file, hoặc dùng API:

```bash
curl -X POST http://localhost:8080/api/v1/admin/problems/import \
  -H "X-API-Key: YOUR_ADMIN_KEY" \
  -F "file=@my-problem.zip"
```

## Subtask Scoring

Test case thuộc subtask: subtask chỉ được điểm khi **tất cả** test case trong subtask đều AC (all-or-nothing).  
Test case không thuộc subtask nào: tính điểm riêng từng case.

## Custom Checker (Special Judge)

Checker nhận 3 đối số: `<input_file> <expected_file> <actual_file>`.  
Exit code `0` = AC, khác = WA.

```cpp
// checker.cpp mẫu
#include <bits/stdc++.h>
using namespace std;
int main(int argc, char* argv[]) {
    ifstream expected(argv[2]), actual(argv[3]);
    double a, b;
    expected >> a; actual >> b;
    return abs(a - b) < 1e-6 ? 0 : 1;
}
```

Upload qua Admin Panel → Problems → nút **⚖️** → nhập source → Compile & Lưu.

## API nhanh

Tất cả request cần header `X-API-Key`.

```bash
# Test thử (đồng bộ, không lưu DB)
curl -X POST http://localhost:8080/api/v1/submissions/test \
  -H "X-API-Key: YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{"problemId":1,"language":"cpp","sourceCode":"#include<bits/stdc++.h>\nusing namespace std;\nint main(){int a,b;cin>>a>>b;cout<<a+b;}"}'

# Nộp bài (async)
curl -X POST http://localhost:8080/api/v1/submissions \
  -H "X-API-Key: YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{"problemId":1,"language":"cpp","sourceCode":"..."}'

# Xem kết quả
curl http://localhost:8080/api/v1/submissions/sub_xxxxxxxxxx \
  -H "X-API-Key: YOUR_KEY"
```

Verdicts: `AC` · `WA` · `TLE` · `MLE` · `RE` · `CE` · `SE` · `PENDING` · `JUDGING`

## Giao diện Web

| URL | Mô tả |
|-----|-------|
| `/admin.html` | Admin Panel (quản lý problem, checker, subtask, test case) |
| `/solve.html` | Trang làm bài (Test Run + Submit) |
| `/docs.html` | API Documentation |

## Biến môi trường

Xem đầy đủ trong `.env.example`. Các biến chính:

| Biến | Mặc định | Mô tả |
|------|----------|-------|
| `DB_PASSWORD` | `change_me` | PostgreSQL password |
| `JUDGE_WORKERS` | `4` | Số worker thread |
| `JUDGE_TESTCASE_BASE_PATH` | `/data/problems` | Thư mục lưu test case |
| `JUDGE_LANG_CPP_IMAGE` | `gcc:13` | Docker image cho C++ |
| `JUDGE_LANG_JAVA_IMAGE` | `eclipse-temurin:21` | Docker image cho Java |
| `JUDGE_LANG_PYTHON_IMAGE` | `python:3.12-slim` | Docker image cho Python |

## License

MIT
