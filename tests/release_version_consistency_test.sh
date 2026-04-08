#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - "$ROOT_DIR" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])

def extract(pattern: str, relative_path: str) -> str:
    text = (root / relative_path).read_text(encoding="utf-8")
    match = re.search(pattern, text, re.MULTILINE)
    if not match:
        raise SystemExit(f"无法从 {relative_path} 提取版本")
    return match.group(1)

expected = extract(r'VERSION:\s+"([^"]+)"', ".github/workflows/docker-build.yml")

checks = {
    "site.ts": extract(r'const VERSION = "([^"]+)";', "vite-frontend/src/config/site.ts"),
    "README.md": extract(r'当前稳定版：`([^`]+)`', "README.md"),
    "docker-compose-v4 backend": extract(r'ghcr\.io/aict666/springboot-backend:([^\s]+)', "docker-compose-v4.yml"),
    "docker-compose-v4 frontend": extract(r'ghcr\.io/aict666/vite-frontend:([^\s]+)', "docker-compose-v4.yml"),
    "docker-compose-v6 backend": extract(r'ghcr\.io/aict666/springboot-backend:([^\s]+)', "docker-compose-v6.yml"),
    "docker-compose-v6 frontend": extract(r'ghcr\.io/aict666/vite-frontend:([^\s]+)', "docker-compose-v6.yml"),
}

for label, actual in checks.items():
    if actual != expected:
        raise SystemExit(f"版本不一致: {label}={actual}, expected={expected}")

print(f"release version consistency check passed: {expected}")
PY
