# Changelog

## [Unreleased]

### feat: tối ưu hiệu năng Batch Runner & đa luồng Worker + lưu stdout/stderr (V23)

- **Batch Runner trong 1 Docker container duy nhất**: Gom toàn bộ $N$ test cases chạy tuần tự trong cùng một container với script đo GNU time và capture output độc lập cho từng testcase. Giảm thời gian chấm 10-20 test cases từ ~8s xuống **~0.8s - 1.2s** (loại bỏ $N \times 450\text{ms}$ Docker setup overhead).
- **Sửa lỗi khởi chạy Worker Pool**: `JudgeWorker` khởi chạy đủ $N$ worker thread song song theo cấu hình `judge.workers=4` thay vì chỉ 1 luồng duy nhất.
- `submission_results.stdout` / `stderr` (cắt 8KB) — API `GET /submissions/{id}` trả trong `testResults[]` để client hiện diff WA.
- `POST /submissions/test` nhận `input` (stdin): chạy một lần, không so expected, trả `stdout`/`stderr` top-level (sửa "Kiểm tra" của client đang gửi sẵn field này).
- Chạy thử theo sample cũng sử dụng Batch Runner và trả `stdout` trên từng `testResults[]` và top-level (sample đầu).

### Đại tu độ tin cậy, bảo mật & lõi chấm (V18–V22)

**Backup & vận hành**
- Thêm `scripts/backup.sh` (pg_dump + test cases + `.env`, xoay vòng 14 bản, rclone lên cloud),
  `restore.sh`, `rotate-db-password.sh`, `recompile-checkers.sh`; cron `deploy/cron/judge-backup`.
- `docs/MIGRATION.md`: runbook chuyển máy chủ. Xoay `DB_PASSWORD` mặc định yếu.

**Bảo mật (V18/V19)**
- API key lưu **SHA-256** (`key_hash` + `key_prefix`), bỏ cột plaintext; raw key chỉ trả 1 lần.
- Sandbox hardening: `--cap-drop ALL`, `no-new-privileges`, `--user 1000`, ulimit nofile/fsize,
  read-only + tmpfs cho cả run/compile/checker. Giới hạn output (`judge.output-limit-bytes`).
- Bỏ mount `docker.sock`; đi qua `tecnativa/docker-socket-proxy`. Actuator chỉ mở `/health`.

**Lõi chấm**
- Image sandbox dẫn xuất `judge-{cpp,java,python}:1` (GNU time + user non-root).
- Đo **CPU time** (TLE) và **peak RSS** (MLE) thật qua GNU time; bỏ throttle `--cpus 0.5`;
  hệ số thời gian theo ngôn ngữ; hỗ trợ time limit < 1s.

**Độ tin cậy**
- `judge()` bỏ `@Transactional`; ghi DB qua `SubmissionPersistenceService` (REQUIRES_NEW).
- Lock Redis có token + gia hạn; `StuckSubmissionJob` reap bài kẹt; worker chống chết.
- Rate limit chuyển sang **Redis** (bucket4j), bền qua restart.

**Chấm nâng cao (V20/V21/V22)**
- Sửa checker java/python; **điểm thành phần** (exit 7 + ratio); **bài interactive** (FIFO);
  `judging_mode` (dừng sớm); comparator **FLOAT** với epsilon.
- Bộ unit test đầu tiên dưới `src/test` (comparator, parse, hash, scoring).

### feat: 3-state problem status (PRIVATE / PUBLIC / CONTEST)

**Migration**: V17 — thêm cột `status VARCHAR(10) NOT NULL DEFAULT 'PRIVATE'` vào `problems`; backfill từ `is_published` (`true → PUBLIC`, `false → PRIVATE`).

#### Các trạng thái

| Status | Người dùng thấy | Submit được |
|--------|-----------------|-------------|
| `PRIVATE` | Không (chỉ admin) | Không |
| `PUBLIC` | Có (hiện trong search, practice) | Có |
| `CONTEST` | Không (chỉ qua contest) | Chỉ khi có `contestId` hợp lệ |

#### Thay đổi backend

- **`Problem.java`**: Thêm field `String status` (default `"PRIVATE"`). `isPublished` vẫn giữ trong DB, tự đồng bộ từ `status` qua `@PrePersist`/`@PreUpdate`.
- **`ProblemService`**: `publish()` → set `PUBLIC`; `unpublish()` → set `PRIVATE`; thêm `setContest()` → set `CONTEST`.
- **`ProblemSpecification.isPublished()`**: Đổi từ `is_published = true` sang `status = 'PUBLIC'`.
- **`ProblemRepository`**: Thêm `findBySlugAndStatus`, `findByStatusOrderByIdAsc`.
- **`ProblemService.getBySlug()`**: Chỉ trả `PUBLIC` problems (không trả `CONTEST`).
- **`SubmissionService`**: Kiểm tra `status` thay vì `isPublished`. `PRIVATE` → 404; `CONTEST` không có `contestId` → 400.
- **`AdminProblemController`**: Thêm `POST /{id}/contest`.
- **`ProblemImportService`**: Import mặc định `status = PRIVATE`.
- **`ProblemResponse`**: Thêm field `status` trong response JSON.

#### Thay đổi frontend (admin.html)

- Badge trạng thái: **Public** (xanh lá) / **Contest** (xanh dương) / **Draft** (xám).
- Nút hành động thay đổi theo trạng thái hiện tại:
  - Nếu không phải PUBLIC → nút `▶ Public`
  - Nếu không phải CONTEST → nút `🏆 Contest`
  - Nếu không phải PRIVATE → nút `🙈 Draft`
- Thay thế `publishProblem` / `unpublishProblem` bằng hàm thống nhất `setProblemStatus(id, action)`.

---

### feat: per-problem allowed languages

**Migration**: V16 — thêm cột `allowed_languages VARCHAR(100) DEFAULT NULL` vào `problems`.

- Lưu dạng chuỗi `"cpp,java,python"`. `NULL` = cho phép tất cả ngôn ngữ.
- `SubmissionService`: từ chối submission với 400 nếu ngôn ngữ không nằm trong danh sách cho phép.
- Admin panel: checkbox C++ / Java / Python trong modal tạo/sửa problem; hiển thị badge ngôn ngữ trong bảng.

---

### feat: Topics & Categories

**Migration**: V13 — bảng `topics`, `categories`, `problem_topics`, `problem_categories` (many-to-many).

- Admin CRUD: `POST/PUT/DELETE /api/v1/admin/topics`, `POST/PUT/DELETE /api/v1/admin/categories`.
- Gắn/gỡ problem vào topic/category qua API.
- Public read: `GET /api/v1/topics`, `GET /api/v1/topics/{slug}`, tương tự categories.
- Problem search: filter theo `topicSlug` và `categorySlug`.

---

### feat: Unpublish & Delete problem

- `POST /api/v1/admin/problems/{id}/unpublish` — ẩn problem.
- `DELETE /api/v1/admin/problems/{id}` — xóa problem kèm toàn bộ test case files.
- FK cascade: `submissions.problem_id` và `submission_results.test_case_id` đổi sang `ON DELETE SET NULL` (V14, V15).

---

### feat: description format (MARKDOWN / HTML)

**Migration**: V9 — thêm `description_format VARCHAR(10) NOT NULL DEFAULT 'MARKDOWN'`.

- solve.html render Markdown qua **marked.js** + sanitize qua **DOMPurify**.
- Admin chọn format khi tạo/sửa problem.

---

### feat: Custom checker (Special Judge)

**Migration**: V7 — thêm `checker_type`, `checker_language`, `checker_source`, `checker_bin_path`.

- Upload checker source → compile tại upload time → lưu binary.
- Khi chấm: nếu `checker_type = CUSTOM`, chạy `checker <input> <expected> <actual>`; exit 0 = AC.

---

### feat: Subtasks

**Migration**: V8 — bảng `subtasks`; `test_cases` thêm `subtask_id FK ON DELETE SET NULL`.

- Gom test cases thành nhóm, mỗi nhóm có điểm riêng.
- Tính điểm all-or-nothing per subtask; nếu test case không thuộc subtask nào thì tính per-case.

---

### feat: Contest mode

**Migration**: V11 — bảng `contests`, `contest_problems`, `contest_participants`; `submissions` thêm `contest_id FK`.

- Admin tạo contest với `start_time`/`end_time`/`is_public`.
- Người dùng đăng ký → submit kèm `contestId`.
- Scoreboard: penalty = phút kể từ lúc bắt đầu + số lần WA × 20.
