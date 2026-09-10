#!/usr/bin/env bash
# ============================================================
# 升降杆样例 - 备份脚本（MySQL 全量 + Redis RDB）
# 用法:   ./backup.sh [保留天数，默认 7]
# 依赖:   mysql-client(mysqldump) / redis-cli / gzip
# 凭据:   经环境变量注入（勿写死脚本内）：
#           MYSQL_USER（默认 root）、MYSQL_PASSWORD（必填）
#           REDIS_PASSWORD（可选；Redis 配置了 requirepass 时必填）
# 定时:   写入 /opt/reason/config/backup.env（600）；root crontab（需读 /var/lib/redis 的 RDB）：
#           30 2 * * * . /opt/reason/config/backup.env && /opt/reason/config/backup.sh >> /var/log/reason-backup.log 2>&1
# 输出:   /opt/reason/backup/mysql/reason_faster_<ts>.sql.gz
#         /opt/reason/backup/redis/dump_<ts>.rdb
# 恢复:   步骤见《云服务器部署手册》"备份与恢复"节（恢复为危险操作：先停平台再执行）
# ============================================================
set -euo pipefail

RETENTION_DAYS="${1:-7}"
BACKUP_ROOT="${BACKUP_ROOT:-/opt/reason/backup}"
DB_NAME="${DB_NAME:-reason_faster}"
MYSQL_USER="${MYSQL_USER:-root}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD env var required (inject via EnvironmentFile, do not hardcode)}"
TS="$(date +%Y%m%d_%H%M%S)"

mkdir -p "$BACKUP_ROOT/mysql" "$BACKUP_ROOT/redis"

# ---- 1) MySQL 全量（单事务一致性快照；含例程/触发器/事件）----
# 云上 dry-run 修复：显式 -u"$MYSQL_USER"（原默认按 OS 用户，专用备份账号会登录失败）
MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -u"$MYSQL_USER" \
  --single-transaction --routines --triggers --events \
  --set-gtid-purged=OFF \
  "$DB_NAME" | gzip > "$BACKUP_ROOT/mysql/${DB_NAME}_${TS}.sql.gz"
echo "[backup] mysql ok -> ${DB_NAME}_${TS}.sql.gz ($(du -h "$BACKUP_ROOT/mysql/${DB_NAME}_${TS}.sql.gz" | cut -f1))"

# ---- 2) Redis RDB（BGSAVE 完成后，按 CONFIG 实际路径拷贝）----
REDIS_CLI=(redis-cli)
if [ -n "${REDIS_PASSWORD:-}" ]; then REDIS_CLI+=(-a "$REDIS_PASSWORD" --no-auth-warning); fi
"${REDIS_CLI[@]}" BGSAVE >/dev/null
for _ in $(seq 1 60); do
  if "${REDIS_CLI[@]}" info persistence | grep -q 'rdb_bgsave_in_progress:0'; then break; fi
  sleep 1
done
RDB_DIR="$("${REDIS_CLI[@]}" CONFIG GET dir | tail -n1)"
RDB_FILE="$("${REDIS_CLI[@]}" CONFIG GET dbfilename | tail -n1)"
cp "${RDB_DIR}/${RDB_FILE}" "$BACKUP_ROOT/redis/dump_${TS}.rdb"
echo "[backup] redis ok -> dump_${TS}.rdb"

# ---- 3) 保留策略（按天清理过期备份）----
find "$BACKUP_ROOT/mysql" -name '*.sql.gz' -mtime +"$RETENTION_DAYS" -delete
find "$BACKUP_ROOT/redis" -name '*.rdb' -mtime +"$RETENTION_DAYS" -delete
echo "[backup] done $(date '+%F %T') retention=${RETENTION_DAYS}d root=${BACKUP_ROOT}"
