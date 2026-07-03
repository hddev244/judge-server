# Runbook: Chuyển Judge Server sang máy chủ khác

Toàn bộ trạng thái của hệ thống nằm ở **3 thứ**: database PostgreSQL, thư mục test case
`/data/problems`, và file `.env`. Cả ba đều được `scripts/backup.sh` gom vào một thư mục
backup duy nhất — chuyển máy chủ = clone repo + restore backup mới nhất.

> Redis chỉ là hàng đợi in-memory (persistence tắt) — không cần chuyển.
> `/tmp/judge` là workspace tạm — không cần chuyển.

## 0. Chuẩn bị máy mới

```bash
# Ubuntu 22.04+ (kernel ≥ 5.15)
sudo apt update && sudo apt install -y git curl rclone ufw
# Docker + compose plugin
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # logout/login lại

# Firewall
sudo ufw default deny incoming
sudo ufw allow <SSH_PORT>/tcp comment 'SSH'
sudo ufw allow 8433/tcp comment 'judge-api'
# (+ 80/443 nếu chạy nginx phía trước — xem deploy/nginx/judge.conf)
sudo ufw enable
```

## 1. Mang code và dữ liệu sang

```bash
git clone git@github.com:hddev244/judge-server.git ~/judge-server
cd ~/judge-server
```

Lấy backup mới nhất về máy mới (chọn một):

```bash
# Cách A — từ cloud (nếu rclone remote đã cấu hình trên máy cũ):
rclone config            # cấu hình cùng remote
rclone copy <remote>:judge-backups/<TIMESTAMP> /var/backups/judge/<TIMESTAMP>

# Cách B — trực tiếp từ máy cũ qua SSH (máy cũ dùng port 24700):
rsync -avz -e 'ssh -p 24700' hddev244@<IP_CU>:/var/backups/judge/<TIMESTAMP>/ \
      /var/backups/judge/<TIMESTAMP>/
```

Khôi phục `.env` từ backup (backup chứa bản sao tên `env`):

```bash
cp /var/backups/judge/<TIMESTAMP>/env ~/judge-server/.env
```

Muốn có backup "chốt sổ" mới nhất trước khi chuyển: trên máy cũ chạy
`./scripts/backup.sh` một lần nữa rồi mới copy (xem mục Cutover).

## 2. Dựng stack và restore

```bash
cd ~/judge-server
./scripts/init-images.sh                 # build/pull image sandbox
docker compose up -d postgres redis      # DB + queue trước
sleep 10                                 # chờ postgres healthy

# LƯU Ý: postgres khởi tạo lần đầu bằng DB_PASSWORD trong .env — vì .env lấy từ
# backup nên mật khẩu khớp sẵn với dump.
./scripts/restore.sh /var/backups/judge/<TIMESTAMP> --yes

docker compose up -d                     # bật judge-api
curl -sf http://localhost:8433/actuator/health
```

## 3. Checker binaries — bắt buộc nếu khác kiến trúc CPU

Checker được lưu dạng **binary đã compile** trong `/data/problems/*/checker/`.
Nếu máy mới khác arch (vd x86_64 → ARM), phải compile lại toàn bộ:

```bash
ADMIN_API_KEY=<admin_key> ./scripts/recompile-checkers.sh
```

(Chạy lại cũng vô hại trên cùng arch — cứ chạy cho chắc.)

## 4. Cutover (chuyển không mất dữ liệu)

1. **Trước 1-2 ngày**: hạ TTL bản ghi DNS xuống 300s (nếu dùng DNS).
2. Dựng xong máy mới theo bước 1–3 với backup gần nhất, smoke test đạt.
3. **Giờ G**: trên máy cũ — chặn submission mới (dừng API: `docker compose stop judge-api`),
   chờ queue cạn (`docker compose exec redis redis-cli llen judge:queue` = 0).
4. Chạy backup cuối: `./scripts/backup.sh`, copy sang máy mới, restore lại lần cuối
   (`restore.sh ... --yes`) rồi `docker compose restart judge-api` trên máy mới.
5. Trỏ DNS / cập nhật IP cho các client (họ gọi qua `X-API-Key`, không đổi key).
6. Máy cũ giữ nguyên (đã stop) 1–2 tuần phòng cần đối chiếu, sau đó mới xoá.

## 5. Smoke test sau migration

```bash
curl -sf http://localhost:8433/actuator/health          # {"status":"UP"}
docker compose ps                                        # tất cả healthy
```

Nộp bài kiểm tra đủ verdict (AC / WA / TLE / MLE / RE) cho từng ngôn ngữ
(cpp, java, python) qua `POST /api/v1/submissions/test` với một bài có sẵn,
xác nhận verdict + `timeMs`/`memoryKb` hợp lý.

## 6. Bật lại backup định kỳ trên máy mới

```bash
sudo mkdir -p /var/backups/judge && sudo chown $USER: /var/backups/judge
sudo touch /var/log/judge-backup.log && sudo chown $USER: /var/log/judge-backup.log
sudo install -m 644 deploy/cron/judge-backup /etc/cron.d/judge-backup
# (sửa username/đường dẫn trong file cron nếu user máy mới khác hddev244)
rclone config        # cấu hình remote cloud nếu chưa
./scripts/backup.sh  # chạy tay 1 lần xác nhận
```

## Phụ lục: dữ liệu nào nằm ở đâu

| Thành phần | Vị trí trên host | Trong backup |
|---|---|---|
| Database (bài, submission, **API keys**) | Docker volume `judge-server_pg-data` | `judgedb.dump` (pg_dump -Fc) |
| Test case + checker binary | `/data/problems` (bind mount) | `problems.tar.gz` |
| Cấu hình + secrets | `~/judge-server/.env` (gitignored) | `env` |
| Code | git `github.com:hddev244/judge-server` | (không — dùng git) |
| Redis | — | không cần |
