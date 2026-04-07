#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

FAKE_BIN="$TMP_DIR/bin"
FAKE_CURL="$FAKE_BIN/curl"
FAKE_DOCKER="$FAKE_BIN/docker"
SCRIPT_COPY="$TMP_DIR/panel_install.sh"
LEGACY_DIR="$TMP_DIR/legacy-working-dir"
HOME_DIR="$TMP_DIR/home"
APP_DIR="$HOME_DIR/flux-panel"

mkdir -p "$FAKE_BIN" "$LEGACY_DIR" "$HOME_DIR"

cat > "$FAKE_DOCKER" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

echo "COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-}" >> "${FAKE_DOCKER_LOG:?}"
echo "$*" >> "${FAKE_DOCKER_LOG:?}"

if [[ "${1:-}" == "compose" && "${2:-}" == "version" ]]; then
  exit 0
fi

exit 0
EOF

chmod +x "$FAKE_DOCKER"

cat > "$FAKE_CURL" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ "$*" == *"ipinfo.io/country"* ]]; then
  echo "US"
  exit 0
fi

output=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -o)
      output="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

if [[ -n "$output" ]]; then
  cat > "$output" <<'YAML'
services:
  backend:
    image: ghcr.io/aict666/springboot-backend:test
YAML
fi
EOF

chmod +x "$FAKE_CURL"
sed '$d' "$ROOT_DIR/panel_install.sh" > "$SCRIPT_COPY"

cat > "$LEGACY_DIR/.env" <<'EOF'
JWT_SECRET=legacy-secret
FRONTEND_PORT=7000
BACKEND_PORT=7001
EOF

cat > "$LEGACY_DIR/docker-compose.yml" <<'EOF'
services:
  backend:
    image: ghcr.io/aict666/springboot-backend:legacy
EOF

export PATH="$FAKE_BIN:$PATH"
export HOME="$HOME_DIR"
export FAKE_DOCKER_LOG="$TMP_DIR/docker.log"
: > "$FAKE_DOCKER_LOG"

cd "$LEGACY_DIR"
source "$SCRIPT_COPY"

ensure_app_workdir

[[ -f "$APP_DIR/.env" ]]
[[ -f "$APP_DIR/docker-compose.yml" ]]
grep -q "legacy-secret" "$APP_DIR/.env"
grep -q "springboot-backend:legacy" "$APP_DIR/docker-compose.yml"

check_docker >/dev/null
run_compose up -d postgres

grep -q "COMPOSE_PROJECT_NAME=flux-panel" "$FAKE_DOCKER_LOG"
grep -q "compose --project-directory $APP_DIR -f $APP_DIR/docker-compose.yml up -d postgres" "$FAKE_DOCKER_LOG"

echo "panel_install runtime dir tests passed"
