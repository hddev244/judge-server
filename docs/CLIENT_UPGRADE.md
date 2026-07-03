# Hướng dẫn nâng cấp cho dự án Client

> Dành cho các backend/dự án đang gọi API của Judge Server. Tài liệu này liệt kê
> **những gì thay đổi** ở đợt cập nhật lớn (V18–V22) và **việc cần làm** để client
> chạy trơn tru. Đọc mục [Checklist](#checklist-nhanh) trước, rồi xem chi tiết bên dưới.

## TL;DR

**Phần lớn tương thích ngược** — các endpoint, header `X-API-Key`, luồng submit/poll/webhook
**không đổi**. Nhưng có **4 điểm bắt buộc kiểm tra** vì có thể làm client hiểu sai dữ liệu:

1. Xuất hiện **verdict mới**: `PC` (điểm thành phần) và `SKIPPED` — code switch/enum theo verdict phải xử lý.
2. `memoryKb` và `timeMs` **giờ có ý nghĩa khác** (số thật thay vì 0 / wall-time).
3. **Raw API key chỉ trả về đúng 1 lần** khi tạo — không lấy lại được qua danh sách nữa.
4. `/actuator/metrics` và `/actuator/info` **không còn public** (chỉ còn `/actuator/health`).

Không có endpoint nào bị xóa hay đổi đường dẫn. Không cần đổi cách ký/verify webhook.

---

## 1. Verdict mới: `PC` và `SKIPPED` (BẮT BUỘC xử lý)

Trường `status` của submission **và** của từng test case giờ có thể nhận thêm:

| Verdict | Ý nghĩa | Xuất hiện khi |
|---------|---------|---------------|
| `PC` | Partial Credit — đúng một phần | Problem dùng custom checker trả điểm thành phần, hoặc bài interactive chấm partial |
| `SKIPPED` | Case bị bỏ qua, không chấm | Problem đặt `judgingMode = STOP_ON_FIRST_FAIL` hoặc `SUBTASK_SKIP` |

Bộ verdict đầy đủ hiện tại:
```
AC · WA · TLE · MLE · RE · CE · SE · PC · SKIPPED · PENDING · JUDGING
```

**Việc cần làm:** nếu client có logic kiểu `if (status === "AC") ... else fail`, hãy rà lại.
`PC` là "đậu một phần" (điểm > 0 nhưng < tối đa), **không phải** AC cũng không phải WA hoàn toàn.
`SKIPPED` nghĩa là hệ thống cố ý không chấm case đó — đừng coi là lỗi.

> Lưu ý: điều này **chỉ ảnh hưởng** nếu bạn tạo problem có custom checker partial / interactive /
> bật `judgingMode`. Problem cũ (checker EXACT, mode mặc định `ALL`) **không bao giờ** trả `PC`/`SKIPPED`.

## 2. `memoryKb` và `timeMs` đổi ý nghĩa

| Trường | Trước đây | Bây giờ |
|--------|-----------|---------|
| `testResults[].memoryKb` | **luôn = 0** (không đo được) | **Peak RSS thật (KB)** |
| `testResults[].timeMs` | Wall-time (gồm cả thời gian khởi động container ~200–500ms) | **CPU time thật** — nhỏ hơn và ổn định hơn nhiều |
| `submission.timeMs` | như trên | CPU time lớn nhất trong các case |
| `submission.memoryKb` | 0/null | **vẫn null** (không tổng hợp ở mức submission) |

**Việc cần làm:**
- Nếu bạn hiển thị bộ nhớ, giờ đọc `testResults[].memoryKb` sẽ ra số thật (trước là 0). Lấy **max** các case nếu muốn hiển thị 1 con số.
- Nếu bạn hiển thị "thời gian chạy" ở mức submission thì đọc `submission.timeMs` (đã là CPU time). Đừng đọc `submission.memoryKb` — nó null; hãy tổng hợp từ `testResults`.
- Con số thời gian sẽ **giảm** so với trước (vì bỏ phần khởi động container) — nếu có ngưỡng cảnh báo/so sánh cứng theo ms cũ thì cần chỉnh lại.

## 3. API Key: raw key chỉ trả về 1 lần

Key giờ được lưu **băm (SHA-256)** trong DB, không lưu bản gốc.

- Key **đang dùng vẫn hoạt động bình thường** — không cần cấp lại, không cần đổi header.
- Khi tạo key mới qua `POST /api/v1/admin/api-keys`: response chứa `key` (bản gốc) **đúng một lần**. **Phải lưu ngay.**
- `GET /api/v1/admin/api-keys` (danh sách) **không còn trả `key`**, chỉ trả `keyPrefix` (8 ký tự đầu) để nhận diện.

Hình dạng `ApiKeyResponse`:
```jsonc
// Khi TẠO key (POST): có "key"
{ "id": 13, "key": "sk_ab12...full", "keyPrefix": "sk_ab12",
  "clientName": "...", "active": true, "admin": false,
  "rateLimitPerHour": 1000, "createdAt": "..." }

// Khi LIỆT KÊ (GET): KHÔNG có "key"
{ "id": 13, "keyPrefix": "sk_ab12", "clientName": "...",
  "active": true, "admin": false, "rateLimitPerHour": 1000, "createdAt": "..." }
```
> JSON dùng `active`/`admin` (không phải `isActive`/`isAdmin`).

**Việc cần làm:** nếu client có luồng tự tạo key rồi đọc lại từ danh sách để lấy giá trị — luồng đó **sẽ hỏng**. Sửa để lưu `key` ngay tại response tạo.

## 4. Actuator không còn public metrics

Chỉ còn `GET /actuator/health` là truy cập được không cần key.
`/actuator/metrics`, `/actuator/info` giờ trả **401**.

**Việc cần làm:** nếu client/monitor đang poll `/actuator/metrics` thì bỏ, chuyển sang `/actuator/health`.

## 5. Rate limit giờ bền qua restart (chú ý nhẹ)

Rate limit lưu ở Redis thay vì bộ nhớ tiến trình → **không còn reset khi Judge Server khởi động lại**.
Header không đổi: `X-RateLimit-Remaining`, `X-RateLimit-Reset`, và vẫn trả **429** khi vượt.

**Việc cần làm:** nếu trước đây client vô tình dựa vào việc restart để "reset quota" thì không còn nữa —
hãy tôn trọng header `X-RateLimit-Remaining`. Nếu bị 429, backoff theo `X-RateLimit-Reset`.

---

## Tính năng mới có thể tận dụng (không bắt buộc)

Tất cả đều **tùy chọn**, mặc định giữ hành vi cũ. Khi tạo/sửa problem
(`POST/PUT /api/v1/admin/problems`) có thể thêm:

| Field | Giá trị | Mặc định |
|-------|---------|----------|
| `comparisonMode` | `EXACT` \| `FLOAT` | `EXACT` |
| `floatEpsilon` | số (dùng khi `FLOAT`) | `1e-6` |
| `judgingMode` | `ALL` \| `STOP_ON_FIRST_FAIL` \| `SUBTASK_SKIP` | `ALL` |

- **So khớp số thực:** đặt `comparisonMode=FLOAT` + `floatEpsilon` cho bài có đáp án số thực.
- **Điểm thành phần / interactive:** upload checker qua
  `POST /api/v1/admin/problems/{id}/checker?language=cpp&type=CUSTOM` (hoặc `type=INTERACTIVE`).
  Xem giao thức trong [README](../README.md).
- **Recompile checker:** `POST /api/v1/admin/problems/{id}/recompile-checker` — cần chạy sau khi
  Judge Server chuyển sang máy chủ khác kiến trúc CPU (binary checker phụ thuộc arch).

ProblemResponse giờ cũng trả thêm `comparisonMode`, `floatEpsilon`, `judgingMode`, và
`checkerType` có thể là `EXACT` / `CUSTOM` / `INTERACTIVE`.

---

## Checklist nhanh

- [ ] Xử lý verdict `PC` và `SKIPPED` trong code phân loại kết quả (mục 1).
- [ ] Đọc `memoryKb` từ `testResults[]` (giờ là số thật); đừng đọc `submission.memoryKb` (null) (mục 2).
- [ ] Cập nhật mọi ngưỡng/so sánh cứng theo `timeMs` (giá trị giờ nhỏ hơn, là CPU time) (mục 2).
- [ ] Luồng tạo API key: lưu `key` ngay từ response tạo, không đọc lại từ danh sách (mục 3).
- [ ] Bỏ poll `/actuator/metrics` → dùng `/actuator/health` (mục 4).
- [ ] Tôn trọng header rate-limit, backoff khi 429 (mục 5).
- [ ] (Nếu áp dụng) Cập nhật UI hiển thị điểm để thể hiện điểm thành phần khi `score` < tối đa nhưng > 0.

## Không cần đổi

- Header xác thực `X-API-Key`, tất cả đường dẫn endpoint.
- Cấu trúc request submit (`POST /api/v1/submissions`, `/submissions/test`).
- Cơ chế webhook: chữ ký HMAC `X-Judge-Signature`, header, retry — **giữ nguyên**.
- Các trường sẵn có trong response (`submissionId`, `problemSlug`, `status`, `score`, `language`, `testResults[].testCaseId/status`).
