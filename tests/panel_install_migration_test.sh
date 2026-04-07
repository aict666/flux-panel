#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

FAKE_BIN="$TMP_DIR/bin"
FAKE_CURL="$FAKE_BIN/curl"
FAKE_DOCKER="$FAKE_BIN/docker"
SCRIPT_COPY="$TMP_DIR/panel_install.sh"

mkdir -p "$FAKE_BIN"

cat > "$FAKE_DOCKER" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

echo "$*" >> "${FAKE_DOCKER_LOG:?}"

if [[ "${1:-}" == "volume" && "${2:-}" == "inspect" ]]; then
  exit 0
fi

if [[ "${1:-}" == "run" ]]; then
  if [[ "$*" == *"--entrypoint sh"* ]]; then
    exit 0
  fi
  exit "${FAKE_DOCKER_RUN_EXIT_CODE:-0}"
fi

exit 0
EOF

chmod +x "$FAKE_DOCKER"

cat > "$FAKE_CURL" <<'EOF'
#!/usr/bin/env bash
echo "US"
EOF

chmod +x "$FAKE_CURL"
sed '$d' "$ROOT_DIR/panel_install.sh" > "$SCRIPT_COPY"

export PATH="$FAKE_BIN:$PATH"
export SQLITE_VOLUME_NAME="sqlite_data"
export BACKEND_LOGS_VOLUME_NAME="backend_logs"
export DB_PORT="5432"
export DB_NAME="flux_panel"
export DB_USER="flux_panel"
export DB_PASSWORD="flux_panel"
export JWT_SECRET="test-secret"

source "$SCRIPT_COPY"

get_backend_image() {
  echo "ghcr.io/aict666/springboot-backend:test"
}

run_failure_case() {
  export FAKE_DOCKER_LOG="$TMP_DIR/docker-failure.log"
  export FAKE_DOCKER_RUN_EXIT_CODE="42"
  : > "$FAKE_DOCKER_LOG"

  set +e
  local output
  output="$(run_sqlite_to_postgres_migration 2>&1)"
  local status=$?
  set -e

  [[ $status -eq 42 ]]
  [[ "$output" == *"🔄 执行 SQLite -> PostgreSQL 数据迁移..."* ]]
  [[ "$output" != *"✅ SQLite -> PostgreSQL 迁移完成"* ]]
}

run_success_case() {
  export FAKE_DOCKER_LOG="$TMP_DIR/docker-success.log"
  export FAKE_DOCKER_RUN_EXIT_CODE="0"
  : > "$FAKE_DOCKER_LOG"

  local output
  output="$(run_sqlite_to_postgres_migration 2>&1)"

  [[ "$output" == *"✅ SQLite -> PostgreSQL 迁移完成"* ]]
}

run_failure_case
run_success_case

echo "panel_install migration tests passed"
