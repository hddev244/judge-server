# Tự triển khai máy chấm và kết nối Thi247.vn

Tài liệu cho **giáo viên / kỹ thuật nhà trường**: dựng `judge-server` trên máy tính (thử nghiệm) hoặc VPS (dùng thật), tạo API key role **GRADER**, rồi gắn vào [thi247.vn](https://thi247.vn).

Phần thao tác trên website Thi247 (tab *Máy chấm riêng*, trạng thái, ý nghĩa điểm) cũng được tóm trong mục 8–10 dưới đây. Bản đầy đủ phía sản phẩm: repo HadavQ `docs/judge_server/TEACHER_BYO_JUDGE.md`.

Repo này: [github.com/hddev244/judge-server](https://github.com/hddev244/judge-server).

---

## Mục lục

1. [Bạn đang làm gì](#1-bạn-đang-làm-gì)
2. [Chọn local hay VPS](#2-chọn-local-hay-vps)
3. [Yêu cầu](#3-yêu-cầu)
4. [Cài Docker](#4-cài-docker)
5. [A. Dựng trên máy tính (local)](#5-a-dựng-trên-máy-tính-local)
6. [B. Dựng trên VPS (production)](#6-b-dựng-trên-vps-production)
7. [Tạo admin key và key GRADER](#7-tạo-admin-key-và-key-grader)
8. [Kết nối với thi247.vn](#8-kết-nối-với-thi247vn)
9. [Xác thực máy](#9-xác-thực-máy)
10. [Nhiều máy chấm](#10-nhiều-máy-chấm)
11. [Vận hành](#11-vận-hành)
12. [Bảo mật](#12-bảo-mật)
13. [Sự cố](#13-sự-cố)
14. [Tham chiếu API](#14-tham-chiếu-api)

---

## 1. Bạn đang làm gì

Thi247 gửi đề + bài của học sinh tới máy của bạn. Máy chạy C++/Java/Python (và Pascal nếu có image) trong Docker sandbox, rồi **gọi ngược** webhook về Thi247.

```
thi247.vn  --HTTPS-->  https://judge.truong-cua-ban.vn   (máy bạn)
           <--webhook--  kết quả đã ký HMAC
```

Role **GRADER** (`gk_…`) chỉ được:

| Được | Không được |
|---|---|
| Nhận/xóa đề mà **chính key này** đã nhận từ Thi247 | Mọi `/api/v1/admin/**` (tạo key, sửa contest, sửa bài người khác) |
| Chấm / chạy thử / xem submission do key này tạo | Nộp vào bài bạn tự soạn trên admin.html |
| `GET /api/v1/meta` | Xem submission của client khác |

**Không** dán admin key (`sk_…`) vào Thi247.

---

## 2. Chọn local hay VPS

| | Máy tính (local) | VPS |
|---|---|---|
| Mục đích | Thử, demo, dev | Lớp học / thi thật |
| URL đăng ký trên **thi247.vn** | Production **từ chối** `localhost` | `https://judge.ten-mien-cua-ban.vn` |
| URL đăng ký trên bản Thi247 **dev** | `http://127.0.0.1:8433` | HTTPS công khai |
| HTTPS | Không cần | **Bắt buộc** (Let’s Encrypt) |
| Webhook về Thi247 | Máy phải gọi được internet | Máy phải gọi được `https://api.thi247.vn` |

Muốn học sinh trên internet nộp bài được → làm mục **B (VPS)**.  
Chỉ thử trên laptop với bản Thi247 local → mục **A**.

---

## 3. Yêu cầu

### Phần mềm

- Linux x86_64 (Ubuntu 22.04/24.04 khuyến nghị). macOS/Windows: chỉ local qua Docker Desktop, không dùng cho thi.
- Docker Engine 24+ và Docker Compose plugin (`docker compose`).
- Với VPS: một **tên miền** trỏ A-record về IPv4 public của máy.

### Phần cứng gợi ý

| Quy mô | vCPU | RAM | Ổ đĩa |
|---|---|---|---|
| Thử / lớp < 20 | 2 | 4 GB | 40 GB |
| Lớp 40–80 | 4 | 8 GB | 80 GB |
| Nhiều ca thi | 8 | 16 GB | 160 GB SSD |

`JUDGE_WORKERS` trong `.env` ≈ số vCPU (để 4 nếu chưa chắc).

### Cổng mạng (VPS)

Chỉ mở: **22** (SSH), **80**, **443**.  
Không mở 5432 (Postgres), 6379 (Redis), 8080/8433 ra internet — Caddy/Nginx proxy từ 443 vào `127.0.0.1`.

---

## 4. Cài Docker

Trên Ubuntu:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker "$USER"
# đăng xuất SSH rồi vào lại để group docker có hiệu lực
docker version
docker compose version
```

---

## 5. A. Dựng trên máy tính (local)

Dùng để thử với backend Thi247 chạy cùng máy (profile `dev`). Production thi247.vn **không** nhận URL này.

### 5.1 Clone và thư mục

```bash
git clone https://github.com/hddev244/judge-server.git
cd judge-server
cp .env.example .env
```

Sửa `.env`:

```dotenv
SERVER_PORT=8433
DB_PASSWORD=<chuỗi dài tự đặt>
JUDGE_WORKERS=2
JUDGE_WORK_BASE=/tmp/judge
JUDGE_TESTCASE_BASE_PATH=/tmp/judge-problems
JUDGE_WEBHOOK_SECRET=
```

```bash
mkdir -p /tmp/judge /tmp/judge-problems
chmod 777 /tmp/judge /tmp/judge-problems
```

(`chmod 777` vì container và Docker daemon cùng ghi; đây là máy local.)

### 5.2 Image sandbox

Lần đầu mất vài phút (tải gcc/Java/Python rồi build):

```bash
./scripts/init-images.sh
```

Image phải có trên **máy host** (không phải trong container): sandbox đi qua `docker-proxy` → socket Docker của máy bạn.

### 5.3 Chạy stack

`docker-compose.local.yml` thêm `host.docker.internal` để webhook từ container về backend trên host.

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
curl -sf http://127.0.0.1:8433/actuator/health
# {"status":"UP", ... "docker":{"status":"UP"}}
```

### 5.4 Tạo key

```bash
./scripts/bootstrap-grader.sh --local
# Ghi .local/credentials.env (đã gitignore)
```

Sang mục [7](#7-tạo-admin-key-và-key-grader) nếu script lỗi; rồi mục 8 với URL `http://127.0.0.1:8433` trên **Thi247 dev**.

---

## 6. B. Dựng trên VPS (production)

Giả sử:

- VPS Ubuntu, user không phải root nhưng có `sudo`
- Tên miền `judge.truong.edu.vn` đã trỏ A về IP VPS (kiểm tra: `dig +short judge.truong.edu.vn`)

### 6.1 Firewall

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

### 6.2 Clone, .env, thư mục

```bash
git clone https://github.com/hddev244/judge-server.git
cd judge-server
cp .env.example .env
```

Sửa `.env` — **đổi mật khẩu DB**, không để `change_me_in_production`:

```dotenv
SERVER_PORT=8080
JUDGE_BIND=127.0.0.1
DB_PASSWORD=<tối thiểu 24 ký tự ngẫu nhiên>
JUDGE_WORKERS=4
JUDGE_WORK_BASE=/tmp/judge
JUDGE_TESTCASE_BASE_PATH=/data/problems
JUDGE_WEBHOOK_SECRET=
```

```bash
sudo mkdir -p /tmp/judge /data/problems
sudo chmod 777 /tmp/judge /data/problems
```

### 6.3 Image sandbox + chạy judge (chỉ lắng nghe localhost)

```bash
./scripts/init-images.sh
docker compose -f docker-compose.yml -f docker-compose.vps.yml up -d --build
curl -sf http://127.0.0.1:8080/actuator/health
```

Overlay VPS bind `127.0.0.1:8080` — internet không vào thẳng cổng judge.

### 6.4 HTTPS với Caddy (Let’s Encrypt)

```bash
sudo apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt-get update
sudo apt-get install -y caddy
```

Sao chép [docs/examples/Caddyfile](./examples/Caddyfile), thay tên miền:

```bash
sudo cp docs/examples/Caddyfile /etc/caddy/Caddyfile
sudo sed -i 's/judge.example.edu.vn/judge.truong.edu.vn/g' /etc/caddy/Caddyfile
sudo systemctl reload caddy
curl -sfI https://judge.truong.edu.vn/actuator/health
```

Caddy tự xin chứng chỉ. Cổng 80 phải mở và DNS phải đúng, nếu không Let’s Encrypt thất bại.

Nginx cũng được: `proxy_pass http://127.0.0.1:8080;` + Certbot. Giữ nguyên header `Host`, `X-API-Key`, body không buffer quá nhỏ (gói đề JSON có thể vài MB).

### 6.5 Tạo key trên VPS

```bash
./scripts/bootstrap-grader.sh --url http://127.0.0.1:8080
```

Lưu file `.local/credentials.env` ngoài git. Key `gk_…` mới dán lên Thi247; URL trên Thi247 là `https://judge.truong.edu.vn` (không phải localhost).

---

## 7. Tạo admin key và key GRADER

Script `scripts/bootstrap-grader.sh`:

- nếu chưa có admin: insert hash SHA-256 vào Postgres (cột `key` plaintext đã bỏ từ V18),
- gọi `POST /api/v1/admin/api-keys` với `"role":"GRADER"`,
- ghi `.local/credentials.env`.

Tự làm tay (khi script không chạy):

```bash
ADMIN_KEY="sk_admin_$(openssl rand -hex 16)"
HASH="$(printf '%s' "$ADMIN_KEY" | sha256sum | awk '{print $1}')"
PREFIX="${ADMIN_KEY:0:8}"

docker compose exec -T postgres psql -U judge -d judgedb -c \
  "INSERT INTO api_keys(key_hash,key_prefix,client_name,is_active,is_admin,rate_limit_per_hour,role)
   VALUES ('${HASH}','${PREFIX}','admin',true,true,9999,'ADMIN');"

# Local overlay: thêm -f docker-compose.local.yml trước exec
# VPS overlay: thêm -f docker-compose.vps.yml

curl -s -X POST http://127.0.0.1:8080/api/v1/admin/api-keys \
  -H "X-API-Key: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"clientName":"thi247","role":"GRADER","rateLimitPerHour":5000}'
# → { "key": "gk_....", "role": "GRADER", ... }   chỉ hiện MỘT lần
```

`rateLimitPerHour` ≥ khoảng `20 × số học sinh` (submit + poll). Lớp 80: `2000` trở lên; mặc định script: `5000`.

Kiểm tra key:

```bash
curl -s -H "X-API-Key: gk_..." https://judge.truong.edu.vn/api/v1/meta
```

Phải thấy `"callerRole":"GRADER"`, `"apiVersion":2`, `"capabilities"` gồm `GRADER_ROLE`, `PACKAGE_UPSERT`, `WEBHOOK_SECRET`.  
`webhookSecretConfigured` còn `false` cho đến khi bạn dán secret ở mục 8.

---

## 8. Kết nối với thi247.vn

1. Đăng nhập giáo viên trên [thi247.vn](https://thi247.vn) → **Bàn làm việc** → **Bài tập lập trình** → tab **Máy chấm riêng**.
2. Cần gói có tính năng *Máy chấm riêng* (`BYO_JUDGE`). Thiếu thì nâng cấp / liên hệ hỗ trợ.
3. **Thêm máy chấm**:
   - Tên: `Judge trường …`
   - URL: `https://judge.truong.edu.vn` (local-dev: `http://127.0.0.1:8433`)
   - API key: `gk_…` vừa tạo
4. Hộp thoại hiện **secret một lần**. Trên máy chấm:

   ```bash
   nano .env    # JUDGE_WEBHOOK_SECRET=<dán>
   ```

   Rồi restart **không** cần build lại:

   ```bash
   # local
   docker compose -f docker-compose.yml -f docker-compose.local.yml up -d
   # VPS
   docker compose -f docker-compose.yml -f docker-compose.vps.yml up -d
   ```

5. Kiểm tra:

   ```bash
   curl -s -H "X-API-Key: gk_..." https://judge.truong.edu.vn/api/v1/meta
   # "webhookSecretConfigured": true
   ```

Callback URL trong hộp thoại (dạng `https://api.thi247.vn/api/webhook/dsa/result/node/<id>`) **không** cần cấu hình trên Caddy — judge tự POST khi chấm xong. VPS chỉ cần **đi ra** được internet (HTTPS).

---

## 9. Xác thực máy

Trên Thi247, thẻ máy → **Xác thực**. Trong ~1 phút trạng thái → **Hoạt động**.

Thi247 sẽ: gọi `/meta` → đẩy bài probe → nộp Python `print(1)` → chờ webhook ký đúng secret.

Sau đó tạo/sửa đề trên Thi247; bấm **Đồng bộ** nếu cột đề chưa đủ.

---

## 10. Nhiều máy chấm

Lặp lại mục 6–9 trên mỗi VPS (hoặc local). **Mỗi máy một key GRADER và một secret.**

Thi247 đẩy cùng bộ đề cho tất cả. Bài chỉ gửi tới máy **Hoạt động** và đề đã `SYNCED`. Máy chết → máy còn lại → máy nền tảng (có hạn mức ngày).

Giữ cùng tag image (`judge-cpp:1` …) trên mọi máy.

---

## 11. Vận hành

### Lệnh thường dùng

```bash
# thay overlay: local.yml hoặc vps.yml
ALIAS=(docker compose -f docker-compose.yml -f docker-compose.vps.yml)

"${ALIAS[@]}" ps
"${ALIAS[@]}" logs -f --tail=200 judge-api
"${ALIAS[@]}" restart judge-api
"${ALIAS[@]}" down          # giữ volume Postgres
"${ALIAS[@]}" up -d --build # sau khi git pull
```

Cập nhật phiên bản:

```bash
cd judge-server
git pull
./scripts/init-images.sh          # nếu Dockerfile sandbox đổi
docker compose -f docker-compose.yml -f docker-compose.vps.yml up -d --build
```

### Sao lưu

Quan trọng nhất: volume Postgres (`judge-server_pg-data`) và thư mục testcase (`JUDGE_TESTCASE_BASE_PATH`).

```bash
docker compose exec -T postgres pg_dump -U judge judgedb | gzip > "judge-$(date +%F).sql.gz"
sudo tar -C /data -czf "problems-$(date +%F).tgz" problems
```

Giữ `.local/credentials.env` và `.env` ở chỗ an toàn (không commit).

### Đổi secret / thu hồi

- Thi247 → **Đổi secret** → cập nhật `.env` → `up -d`. Secret cũ còn hiệu lực **24 giờ**.
- Thu hồi: `PATCH /api/v1/admin/api-keys/{id}/deactivate` với admin key, rồi gỡ máy trên Thi247.

### Việc không làm

- Không sửa bài slug `g{id}_…` trên `admin.html` — Thi247 ghi đè.
- Không tăng `JUDGE_WORKERS` vượt số CPU.
- Không `docker compose down -v` trên máy đang thi (xóa DB).

---

## 12. Bảo mật

- Key GRADER chỉ chấm bài **do Thi247 đẩy**. Admin key chỉ nằm trên VPS.
- Compose đã dùng `docker-socket-proxy` (không mount raw `/var/run/docker.sock` vào `judge-api`).
- Sandbox: `--network none`, user không root, giới hạn CPU/RAM.
- Code học sinh lưu trên máy bạn — trách nhiệm bảo vệ dữ liệu cá nhân (NĐ 13/2023/NĐ-CP).
- Đặt mật khẩu DB mạnh; không mở Postgres ra ngoài.
- Caddy/Let’s Encrypt: giữ đồng hồ NTP đúng (cert sẽ fail nếu lệch giờ).

---

## 13. Sự cố

| Hiện tượng | Cách xử |
|---|---|
| `actuator/health` không UP | `logs judge-api`; Postgres/Redis chưa healthy; sai `SERVER_PORT` |
| `docker` component DOWN | Daemon Docker trên host chết; `docker-proxy` không chạy |
| `init-images.sh` lỗi path `stdc++.h` | Đã vá: Dockerfile C++ tìm header động. `git pull` rồi chạy lại |
| Let’s Encrypt fail | DNS chưa về VPS; cổng 80 bị chặn; A-record IPv6 lệch |
| Thi247: “phải dùng https” | Đang dán `http://` trên production |
| Thi247: “địa chỉ nội bộ” | `localhost` / IP LAN trên thi247.vn |
| `callerRole` không phải GRADER | Dán nhầm `sk_…`; tạo lại `"role":"GRADER"` |
| Xác thực treo | Chưa `JUDGE_WEBHOOK_SECRET` + restart; VPS không ra được `api.thi247.vn` (`curl -I https://api.thi247.vn`) |
| Sandbox không chạy | Chưa `init-images.sh` trên **host**; `docker images \| grep judge-` |
| Cổng 8080/8433 bị chiếm | Đổi `SERVER_PORT` trong `.env`, sửa Caddy `reverse_proxy` cho khớp |

---

## 14. Tham chiếu API

Base URL = gốc máy (không thêm `/api` khi đăng ký trên Thi247). Header: `X-API-Key`.

| Method | Path | Ai gọi | Việc |
|---|---|---|---|
| GET | `/actuator/health` | Ai cũng | Health, không cần key |
| GET | `/api/v1/meta` | GRADER/ADMIN | Protocol, capabilities, images |
| PUT | `/api/v1/grader/problems/{externalKey}` | GRADER | Thi247 đẩy gói đề |
| GET | `/api/v1/grader/problems/{externalKey}` | GRADER | Đọc gói |
| DELETE | `/api/v1/grader/problems/{externalKey}` | GRADER | Xóa bài đã đẩy |
| POST | `/api/v1/submissions` | GRADER | Nộp chấm (chỉ bài của key) |
| POST | `/api/v1/submissions/test` | GRADER | Chạy thử |
| GET | `/api/v1/submissions/{id}` | GRADER | Kết quả |
| POST | `/api/v1/admin/api-keys` | ADMIN | Tạo key; body `{ "clientName", "role":"GRADER", "rateLimitPerHour" }` |

Webhook máy → Thi247: POST JSON, header `X-Judge-Signature` = HMAC-SHA256(body, `JUDGE_WEBHOOK_SECRET`).

---

## File liên quan trong repo

| File | Việc |
|---|---|
| [`.env.example`](../.env.example) | Mẫu biến môi trường |
| [`docker-compose.yml`](../docker-compose.yml) | API + Postgres + Redis + docker-proxy |
| [`docker-compose.local.yml`](../docker-compose.local.yml) | `host.docker.internal` (dev) |
| [`docker-compose.vps.yml`](../docker-compose.vps.yml) | Overlay VPS; set `JUDGE_BIND=127.0.0.1` trong `.env` |
| [`scripts/init-images.sh`](../scripts/init-images.sh) | Build image sandbox |
| [`scripts/bootstrap-grader.sh`](../scripts/bootstrap-grader.sh) | Admin + GRADER |
| [`docs/examples/Caddyfile`](./examples/Caddyfile) | HTTPS Let’s Encrypt |
