# Judge Server

**Judge as a Service** — hệ thống chấm bài lập trình tự động hỗ trợ C++, Java, Python. Mỗi submission chạy trong Docker sandbox cô lập, trả verdict trong vài giây.

## Tính năng

- **Chấm bài tự động** — C++ (GCC 13), Java 21, Python 3.12
- **Docker sandbox** — `--network none`, memory/CPU limit, read-only filesystem
- **Test Run** — chạy test mẫu đồng bộ, không lưu DB
- **Queue** — Redis BRPOP, 4 worker threads song song
- **Webhook** — callback khi chấm xong, HMAC-SHA256 signature, auto-retry
- **Rate limiting** — Bucket4j, per API key
- **Admin Panel** — quản lý problem, test case (upload ZIP batch), submissions, API keys
- **Trang làm bài** — editor với template, kiểm tra + nộp bài

## Stack

| Thành phần | Công nghệ |
|-----------|-----------|
| API | Spring Boot 3.2, Java 21 (virtual threads) |
| Database | PostgreSQL 16 + Flyway |
| Queue | Redis 7 BRPOP |
| Sandbox | Docker-in-Docker (socket mount) |
| Rate limit | Bucket4j |
| Build | Maven, multi-stage Dockerfile |

## Cấu trúc

```
src/main/java/com/judge/
├── api/          REST controllers + DTOs
├── domain/       JPA entities
├── service/      Business logic
├── judge/        DockerRunner, JudgeService, JudgeWorker
├── queue/        Redis queue
├── webhook/      Async callback + retry
├── security/     ApiKeyFilter, RateLimitFilter
└── exception/    GlobalExceptionHandler
```

## Khởi chạy

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

## API nhanh

Tất cả request cần header `X-API-Key`.

```bash
# Test thử (đồng bộ, không lưu DB)
curl -X POST http://localhost:8080/api/v1/submissions/test \
  -H "X-API-Key: YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{"problemId":1,"language":"cpp","sourceCode":"..."}'

# Nộp bài
curl -X POST http://localhost:8080/api/v1/submissions \
  -H "X-API-Key: YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{"problemId":1,"language":"cpp","sourceCode":"..."}'
```

## Giao diện

| URL | Mô tả |
|-----|-------|
| `/docs.html` | API Documentation |
| `/solve.html` | Trang làm bài |
| `/admin.html` | Admin Panel |

## Cấu hình

| Biến môi trường | Mô tả |
|----------------|-------|
| `DB_URL` | PostgreSQL JDBC URL |
| `REDIS_HOST` | Redis hostname (mặc định `localhost`) |
| `JUDGE_TESTCASE_BASE_PATH` | Thư mục lưu test case (mặc định `/data/problems`) |

## License

MIT
