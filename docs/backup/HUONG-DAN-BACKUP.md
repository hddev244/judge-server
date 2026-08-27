# Hướng dẫn backup & khôi phục Judge Server

Cập nhật: 2026-08-15  
Máy: `100.88.3.85` (Tailscale) · user `hddev244` · SSH port `24700`  
Repo: `git@github.com:hddev244/judge-server.git`  
Thư mục project: `/home/hddev244/judge-server`

Tài liệu này + bản sao script nằm trên Google Drive:

- `gdrive:judge-ops/backup-docs/` — **không bị xóa** bởi cron (đọc hướng dẫn / lấy script)
- `gdrive:judge-backups/` — 14 ngày dump thật (cron `rclone sync` mỗi đêm)

---

## 1. Judge lưu dữ liệu ở đâu

Ba chỗ bền + hai chỗ tạm:

| Nơi | Loại | Có trong backup? | Ghi chú |
|-----|------|------------------|---------|
| Volume Docker `judge-server_pg-data` | Postgres 16 `judgedb` | Có — `judgedb.dump` | Đề, test metadata, **source nộp**, điểm, kết quả từng test, contest trên judge, API key |
| `/data/problems` | File test + checker | Có — `problems.tar.gz` | DB chỉ lưu `input_path` / `output_path` |
| `/home/hddev244/judge-server/.env` | Cấu hình + secret | Có — file `env` trong mỗi bản | Cần để `docker compose up` trên máy mới |
| Redis | Hàng đợi chấm | Không | Cố ý `--save ""`, không persist |
| `/tmp/judge` | Thư mục biên dịch/chạy tạm | Không | Xóa được; bài đang chấm lúc sập phải nộp lại |

**Source bài nộp không phải file rời.** Cột `submissions.source_code` (TEXT) nằm trong Postgres. Kết quả từng test nằm ở `submission_results`.

Bảng chính (số liệu tham chiếu 2026-08-15):

- `problems` — đề, đề bài, checker source, giới hạn thời gian/bộ nhớ
- `test_cases` — đường dẫn file in/out, điểm, sample
- `subtasks`
- `submissions` — user, ngôn ngữ, source, status, score, time_ms, memory_kb, lỗi
- `submission_results` — verdict từng test
- `contests`, `contest_participants`, `contest_problems`
- `api_keys`, `webhook_retries`, `categories`, `topics`, tags

**Không nằm trong backup judge:** điểm / bài nộp contest trên Thi247 (Mongo `contest_submissions`, `contest_scores` trên `api.thi247.vn`).

---

## 2. Mỗi bản backup gồm gì

Thư mục: `/var/backups/judge/<YYYY-MM-DD_HHMM>/`

```
2026-08-15_0230/
  judgedb.dump      # pg_dump -Fc (custom format)
  problems.tar.gz   # tar của /data/problems
  env               # bản sao .env lúc dump (có secret)
  manifest.json     # timestamp, git commit, sha256 từng file
```

`manifest.json` dùng để `restore.sh` từ chối file hỏng.

Giữ local: `KEEP_LOCAL=14` (mặc định). Cron Drive cũng chỉ còn 14 ngày vì `rclone sync` **xóa** trên Drive những ngày local đã xoá.

---

## 3. Lịch tự động

File cron: `/etc/cron.d/judge-backup`  
Mẫu trong repo: `deploy/cron/judge-backup`

```
30 2 * * *  hddev244  backup.sh  →  /var/log/judge-backup.log
```

Biến môi trường cron:

- `HOME=/home/hddev244` — để rclone đọc `~/.config/rclone/rclone.conf`
- `RCLONE_REMOTE=gdrive:judge-backups`
- `PATH` gồm `/usr/bin` (rclone official)

Cài lại cron sau khi sửa mẫu:

```bash
sudo install -m 644 /home/hddev244/judge-server/deploy/cron/judge-backup /etc/cron.d/judge-backup
```

Chạy tay (dump mới + xoay 14 ngày + đẩy Drive):

```bash
ssh -p 24700 hddev244@100.88.3.85
/home/hddev244/judge-server/scripts/backup.sh
```

---

## 4. rclone / Google Drive

- rclone trên máy judge: bản official (không dùng gói apt 1.53)
- Config: `/home/hddev244/.config/rclone/rclone.conf` (remote `gdrive:`, scope `drive`)
- Config gốc cũng có trên máy dev: `~/.config/rclone/rclone.conf`

Lệnh kiểm tra:

```bash
rclone listremotes
rclone lsd gdrive:judge-backups
rclone ls gdrive:judge-backups
rclone check /var/backups/judge gdrive:judge-backups --one-way
```

Kéo về máy bất kỳ đã có remote `gdrive:`:

```bash
# Một ngày
rclone copy gdrive:judge-backups/2026-08-15_0230 ./2026-08-15_0230

# Cả 14 ngày
rclone sync gdrive:judge-backups /var/backups/judge
```

**Cảnh báo:** rclone đang dùng client_id dùng chung, sẽ ngừng trong 2026. Tạo client_id riêng: https://rclone.org/drive/#making-your-own-client-id

**Không** đặt file lẻ vào `gdrive:judge-backups/` — đêm sau `rclone sync` sẽ xóa. Tài liệu để ở `gdrive:judge-ops/backup-docs/`.

File `env` trong mỗi bản backup chứa secret. Không share thư mục Drive.

---

## 5. Khôi phục khi sự cố

Script: `scripts/restore.sh`

```
restore.sh <backup_dir> [--yes] [--db <tên_db>]
```

- Kiểm checksum 3 file với `manifest.json`
- `pg_restore --clean --if-exists --no-owner` vào Postgres
- Nếu **không** có `--db`: giải `problems.tar.gz` đè `/data/problems`
- In số dòng `problems`, `test_cases`, `submissions`, `api_keys`

`restore.sh` **không** tự copy `env` → `.env`. Phải làm tay nếu `.env` mất.

### 5.1 Máy còn sống — chỉ hỏng DB / file test

```bash
ssh -p 24700 hddev244@100.88.3.85
cd /home/hddev244/judge-server

ls /var/backups/judge

docker compose stop judge-api
./scripts/restore.sh /var/backups/judge/2026-08-15_0230
# hoặc: ./scripts/restore.sh /var/backups/judge/2026-08-15_0230 --yes
docker compose start judge-api
```

Đổi ngày thư mục cho đúng bản muốn về.

### 5.2 Máy chết / disk mất — dựng lại từ Drive

Trên máy mới cần: Docker, rclone + remote `gdrive:`, clone `judge-server`, SSH key nếu pull git.

```bash
# 1) Code
git clone git@github.com:hddev244/judge-server.git ~/judge-server
cd ~/judge-server

# 2) Kéo backup
mkdir -p /var/backups/judge
rclone copy gdrive:judge-backups/2026-08-15_0230 /var/backups/judge/2026-08-15_0230

# 3) Secret + thư mục data
cp /var/backups/judge/2026-08-15_0230/env .env
mkdir -p /tmp/judge /data/problems

# 4) Ảnh sandbox ngôn ngữ + stack
./scripts/init-images.sh
docker compose up -d
# đợi postgres healthy: docker compose ps

# 5) Nạp dữ liệu
./scripts/restore.sh /var/backups/judge/2026-08-15_0230 --yes

# 6) (tuỳ chọn) checker binary khác CPU
# ADMIN_API_KEY=... ./scripts/recompile-checkers.sh
```

Cài lại cron:

```bash
sudo install -m 644 deploy/cron/judge-backup /etc/cron.d/judge-backup
```

Copy lại `~/.config/rclone/rclone.conf` nếu muốn đêm sau tiếp tục đẩy Drive.

### 5.3 Thử restore, không đụng bản live

Nạp dump vào DB phụ, **không** ghi file test:

```bash
cd /home/hddev244/judge-server
./scripts/restore.sh /var/backups/judge/2026-08-15_0230 --db judgedb_verify --yes
```

Xong thì:

```bash
docker compose exec -T postgres dropdb -U judge judgedb_verify
```

---

## 6. Sau khi restore

- Bài **đã chấm xong** (`finished_at` có giá trị) nằm trong dump → còn.
- Bài đang trong hàng đợi Redis / `/tmp/judge` lúc sập → mất, học sinh nộp lại.
- API key trong dump: Thi247 vẫn gọi được nếu `.env` + `api_keys` khớp.
- Nếu restore sang CPU khác (amd64 → arm): chạy `recompile-checkers.sh` vì binary checker trong `/data/problems/*/checker/` phụ thuộc kiến trúc.

---

## 7. Script trong bộ này

| File | Việc |
|------|------|
| `scripts/backup.sh` | Dump Postgres + tar test + copy `.env` + xoay 14 ngày + rclone sync |
| `scripts/restore.sh` | Checksum + pg_restore + giải tar |
| `scripts/recompile-checkers.sh` | Gọi API admin biên dịch lại checker CUSTOM |
| `scripts/deploy.sh` | Build image sandbox + rebuild API + health check |
| `scripts/init-images.sh` | Kéo gcc/temurin/python, build `judge-cpp:1` / `judge-java:1` / `judge-python:1` |
| `scripts/rotate-db-password.sh` | Đổi mật khẩu Postgres (ops, không phải backup) |
| `deploy/cron/judge-backup` | Cron 02:30 + `RCLONE_REMOTE` |

Biến môi trường `backup.sh` hiểu:

- `BACKUP_ROOT` — mặc định `/var/backups/judge`
- `KEEP_LOCAL` — mặc định `14`
- `RCLONE_REMOTE` — mặc định `gdrive:judge-backups` (cron set sẵn; nếu trống thì lấy remote rclone đầu + suffix `judge-backups`)

---

## 8. Kiểm tra sức khỏe backup

```bash
# Log đêm
tail -50 /var/log/judge-backup.log

# Bản local
ls -1 /var/backups/judge
du -sh /var/backups/judge/*

# Khớp Drive
rclone check /var/backups/judge gdrive:judge-backups --one-way

# Số dòng live
cd ~/judge-server
docker compose exec -T postgres psql -U judge -d judgedb -c "
SELECT 'problems' t, count(*) FROM problems
UNION ALL SELECT 'test_cases', count(*) FROM test_cases
UNION ALL SELECT 'submissions', count(*) FROM submissions
UNION ALL SELECT 'submission_results', count(*) FROM submission_results;
"
```

---

## 9. SSH

```bash
ssh -p 24700 hddev244@100.88.3.85
```

Stack Docker: `judge-api` (cổng 8433), `postgres:16`, `redis:7` (không persist), `docker-proxy`.
