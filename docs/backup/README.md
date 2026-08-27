# Judge Server — bộ tài liệu backup

Thư mục Drive này **không** bị cron xóa. Dump 14 ngày nằm ở `gdrive:judge-backups/`.

| File | Nội dung |
|------|----------|
| [HUONG-DAN-BACKUP.md](HUONG-DAN-BACKUP.md) | Hướng dẫn đầy đủ: dữ liệu nào được sao, cron, rclone, restore |
| [CHEATSHEET.md](CHEATSHEET.md) | Lệnh copy-paste khi sự cố |
| `scripts/` | Bản sao `backup.sh`, `restore.sh` và script ops liên quan |
| `deploy/cron/judge-backup` | Cron 02:30 + remote Drive |

Máy: `ssh -p 24700 hddev244@100.88.3.85`  
Cập nhật: 2026-08-15
