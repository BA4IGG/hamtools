#!/usr/bin/env bash
# ham_users 表备份脚本。配合 crontab 每天跑一次。
# 用法：bash scripts/backup-db.sh           # 备份到 ./backups
#      BACKUP_DIR=/data/backup bash scripts/backup-db.sh
set -euo pipefail

# 载入 server 目录下的 .env（脚本在 server/scripts/ 里，往上退一级）
SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -f "$SERVER_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$SERVER_DIR/.env"
  set +a
fi

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-hamtools}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_NAME="${DB_NAME:-hamtools}"

BACKUP_DIR="${BACKUP_DIR:-$SERVER_DIR/backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
OUT="$BACKUP_DIR/${DB_NAME}-${STAMP}.sql.gz"

MYSQL_PWD="$DB_PASSWORD" mysqldump \
  --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" \
  --single-transaction --quick --default-character-set=utf8mb4 \
  "$DB_NAME" | gzip > "$OUT"

echo "已备份：$OUT ($(du -h "$OUT" | cut -f1))"

# 清理过期备份
find "$BACKUP_DIR" -name "${DB_NAME}-*.sql.gz" -type f -mtime +"$KEEP_DAYS" -delete
echo "已清理 ${KEEP_DAYS} 天前的备份"
