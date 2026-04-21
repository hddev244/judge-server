# Prompt: Implement "Import Problem from ZIP" in Next.js Admin

## Nhiệm vụ

Triển khai chức năng **Import Problem từ file ZIP** cho trang admin Next.js. Chức năng này cho phép admin upload một file ZIP chứa đầy đủ thông tin bài toán (metadata, test cases, checker) lên Judge Server.

---

## Kiến trúc hiện tại cần nắm

- **Frontend**: Next.js App Router (hoặc Pages Router — xem codebase để xác định)
- **Backend proxy**: Mọi request đến Judge Server đều đi qua Next.js API Route server-side. **API key tuyệt đối không được xuất hiện ở client-side.**
- **Judge Server**: Spring Boot tại `JUDGE_BASE_URL` (env var server-side)
- **Auth**: Header `X-API-Key: <JUDGE_API_KEY>` (env var server-side)

---

## Endpoint Judge Server cần gọi

```
POST /api/v1/admin/problems/import
Content-Type: multipart/form-data

Field:
  file: <binary ZIP file>

Response 200:
{
  "id": 1,
  "slug": "a-plus-b",
  "title": "A + B Problem",
  "description": "...",
  "descriptionFormat": "MARKDOWN",
  "timeLimitMs": 1000,
  "memoryLimitKb": 262144,
  "difficulty": "easy",
  "tags": ["math"],
  "published": false,
  "checkerType": "EXACT"
}

Response 400: { "error": "BAD_REQUEST", "message": "..." }
Response 401: { "error": "UNAUTHORIZED", "message": "Invalid or missing API Key" }
```

---

## Cấu trúc ZIP hợp lệ

```
problem.yml          ← BẮT BUỘC
tests/
  1.in
  1.out
  2.in
  2.out
  sample_1.in        ← prefix "sample" hoặc "ex" → is_sample = true
  sample_1.out
subtasks.yml         ← tùy chọn
checker.cpp          ← tùy chọn (hoặc checker.java / checker.py)
```

**`problem.yml` mẫu:**

```yaml
slug: a-plus-b
title: "A + B Problem"
description: "Cho hai số nguyên a và b. Tính tổng a + b."
description_format: MARKDOWN
timeLimitMs: 1000
memoryLimitKb: 262144
difficulty: easy
tags:
  - math
  - basic
```

---

## Yêu cầu triển khai

### 1. Next.js API Route — proxy upload

Tạo API route xử lý multipart upload và forward sang Judge Server **kèm API key server-side**.

File: `app/api/judge/admin/problems/import/route.ts`

```ts
import { NextRequest, NextResponse } from 'next/server';

const JUDGE_BASE = process.env.JUDGE_BASE_URL!;
const JUDGE_KEY  = process.env.JUDGE_API_KEY!;

export async function POST(req: NextRequest) {
  // 1. Lấy FormData từ request
  const formData = await req.formData();
  const file = formData.get('file') as File | null;

  if (!file) {
    return NextResponse.json({ message: 'Thiếu file ZIP' }, { status: 400 });
  }

  // 2. Build FormData để forward sang Judge Server
  const upstream = new FormData();
  upstream.append('file', file, file.name);

  // 3. Gọi Judge Server với API key (KHÔNG để key ở client)
  const res = await fetch(`${JUDGE_BASE}/api/v1/admin/problems/import`, {
    method: 'POST',
    headers: { 'X-API-Key': JUDGE_KEY },
    body: upstream,
  });

  const data = await res.json().catch(() => ({}));

  return NextResponse.json(data, { status: res.status });
}

// Tăng giới hạn body size cho file lớn
export const config = {
  api: { bodyParser: false },
};
```

> **Lưu ý nếu dùng Pages Router**: tạo `pages/api/judge/admin/problems/import.ts` với `export const config = { api: { bodyParser: false } }` và dùng `multer` hoặc `formidable` để parse.

---

### 2. React Component — UI Upload

Tạo component `ImportProblemZip` với các yêu cầu UI sau:

**Trạng thái (states):**
- `idle` — hiển thị drop zone
- `previewing` — đã chọn file, hiển thị tên file + nút Import
- `uploading` — đang upload, hiển thị spinner, disable nút
- `success` — import thành công, hiển thị thông tin problem vừa tạo (slug, title) + link đến trang problem
- `error` — hiển thị message lỗi từ server

**Yêu cầu UX:**
- Drag & drop hoặc click để chọn file `.zip`
- Chỉ chấp nhận file `.zip` (validate `file.type === 'application/zip'` hoặc tên file kết thúc `.zip`)
- Hiển thị tên file và kích thước sau khi chọn
- Nút "Import" chỉ enabled khi đã chọn file hợp lệ
- Khi thành công: toast/alert "Import thành công" + tên bài + nút "Xem problem"
- Khi lỗi: hiển thị message lỗi từ server (ví dụ: "Slug đã tồn tại", "problem.yml không hợp lệ")
- Nút reset để chọn file khác

**Gọi API (từ client, đến Next.js API route — KHÔNG gọi trực tiếp Judge Server):**

```ts
async function importZip(file: File) {
  const form = new FormData();
  form.append('file', file);

  const res = await fetch('/api/judge/admin/problems/import', {
    method: 'POST',
    body: form,
    // KHÔNG set Content-Type — browser tự set boundary
  });

  const data = await res.json();
  if (!res.ok) throw new Error(data.message || `HTTP ${res.status}`);
  return data; // ProblemResponse
}
```

---

### 3. Tích hợp vào trang admin

Tìm trang quản lý problems trong codebase (thường là `app/admin/problems/page.tsx` hoặc tương tự) và thêm:

- Nút **"📦 Import ZIP"** cạnh nút "Tạo problem"
- Khi click: mở modal/drawer chứa component `ImportProblemZip`
- Sau khi import thành công: đóng modal và **reload danh sách problems**

---

## Biến môi trường cần có

Kiểm tra `.env.local` có 2 biến sau chưa, nếu chưa thì thêm vào:

```env
JUDGE_BASE_URL=http://localhost:8433
JUDGE_API_KEY=sk_admin_2de8a29c0bc444499d9c3369
```

> `JUDGE_API_KEY` là server-side only — **không được** prefix `NEXT_PUBLIC_`.

---

## Lưu ý quan trọng

1. **Không để `JUDGE_API_KEY` trong client bundle** — chỉ dùng trong API Route (server-side).
2. **Không gọi `JUDGE_BASE_URL` trực tiếp từ browser** — mọi fetch phải qua `/api/judge/...`.
3. File ZIP có thể lớn (nhiều test case) — đảm bảo Next.js không giới hạn body size quá nhỏ. Nếu cần, thêm vào `next.config.js`:
   ```js
   experimental: {
     serverActions: { bodySizeLimit: '52mb' }
   }
   ```
4. Judge Server đã cấu hình `max-file-size: 50MB` — không cần lo phía backend.
5. Sau khi import, problem ở trạng thái **Draft** (chưa published). Admin cần vào trang problem để publish riêng.

---

## Kiểm thử

Sau khi triển khai, test với ZIP sau (tạo thủ công):

```
my-problem.zip
├── problem.yml
└── tests/
    ├── sample_1.in    (nội dung: "3 5")
    ├── sample_1.out   (nội dung: "8")
    ├── 1.in           (nội dung: "1 2")
    └── 1.out          (nội dung: "3")
```

`problem.yml`:
```yaml
slug: test-import-001
title: "Test Import"
timeLimitMs: 1000
memoryLimitKb: 262144
description: "Test"
```

Kết quả mong đợi: problem `test-import-001` xuất hiện trong danh sách với 2 test cases (1 sample).
