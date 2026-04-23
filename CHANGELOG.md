# Changelog

## [Unreleased]

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
