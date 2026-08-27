# Cheat sheet — backup / restore judge

```bash
# SSH
ssh -p 24700 hddev244@100.88.3.85

# Dump + đẩy Drive ngay
~/judge-server/scripts/backup.sh

# Xem bản local / Drive
ls /var/backups/judge
rclone lsd gdrive:judge-backups

# Restore máy còn sống (đổi ngày)
cd ~/judge-server
docker compose stop judge-api
./scripts/restore.sh /var/backups/judge/2026-08-15_0230
docker compose start judge-api

# Kéo 1 bản từ Drive
rclone copy gdrive:judge-backups/2026-08-15_0230 /var/backups/judge/2026-08-15_0230

# Máy mới: .env rồi nạp
cd ~/judge-server
cp /var/backups/judge/2026-08-15_0230/env .env
./scripts/init-images.sh && docker compose up -d
./scripts/restore.sh /var/backups/judge/2026-08-15_0230 --yes

# Thử dump, không đụng live
./scripts/restore.sh /var/backups/judge/2026-08-15_0230 --db judgedb_verify --yes

# Log
tail -50 /var/log/judge-backup.log
```

Tài liệu đầy đủ: `HUONG-DAN-BACKUP.md`
