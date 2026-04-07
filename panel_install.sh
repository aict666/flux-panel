#!/bin/bash
set -e

# 解决 macOS 下 tr 可能出现的非法字节序列问题
export LANG=en_US.UTF-8
export LC_ALL=C



# 全局下载地址配置
DOCKER_COMPOSEV4_URL="https://github.com/aict666/flux-panel/releases/latest/download/docker-compose-v4.yml"
DOCKER_COMPOSEV6_URL="https://github.com/aict666/flux-panel/releases/latest/download/docker-compose-v6.yml"
POSTGRES_CONTAINER_NAME="flux-postgres"
SQLITE_VOLUME_NAME="sqlite_data"
BACKEND_LOGS_VOLUME_NAME="backend_logs"
DEFAULT_DB_NAME="flux_panel"
DEFAULT_DB_USER="flux_panel"
PANEL_COMPOSE_PROJECT_NAME="${PANEL_COMPOSE_PROJECT_NAME:-flux-panel}"
PANEL_APP_DIR="${PANEL_APP_DIR:-$HOME/flux-panel}"
PANEL_ENV_FILE="${PANEL_ENV_FILE:-$PANEL_APP_DIR/.env}"
PANEL_COMPOSE_FILE="${PANEL_COMPOSE_FILE:-$PANEL_APP_DIR/docker-compose.yml}"
INITIAL_WORKDIR="${INITIAL_WORKDIR:-$(pwd)}"

COUNTRY=$(curl -s https://ipinfo.io/country)
if [ "$COUNTRY" = "CN" ]; then
    # 拼接 URL
    DOCKER_COMPOSEV4_URL="https://ghfast.top/${DOCKER_COMPOSEV4_URL}"
    DOCKER_COMPOSEV6_URL="https://ghfast.top/${DOCKER_COMPOSEV6_URL}"
fi



# 根据IPv6支持情况选择docker-compose URL
get_docker_compose_url() {
  if check_ipv6_support > /dev/null 2>&1; then
    echo "$DOCKER_COMPOSEV6_URL"
  else
    echo "$DOCKER_COMPOSEV4_URL"
  fi
}

# 检查 docker-compose 或 docker compose 命令
check_docker() {
  if command -v docker-compose &> /dev/null; then
    DOCKER_CMD="docker-compose"
  elif command -v docker &> /dev/null; then
    if docker compose version &> /dev/null; then
      DOCKER_CMD="docker compose"
    else
      echo "错误：检测到 docker，但不支持 'docker compose' 命令。请安装 docker-compose 或更新 docker 版本。"
      exit 1
    fi
  else
    echo "错误：未检测到 docker 或 docker-compose 命令。请先安装 Docker。"
    exit 1
  fi
  echo "检测到 Docker 命令：$DOCKER_CMD"
}

# 检测系统是否支持 IPv6
check_ipv6_support() {
  echo "🔍 检测 IPv6 支持..."

  # 检查是否有 IPv6 地址（排除 link-local 地址）
  if ip -6 addr show | grep -v "scope link" | grep -q "inet6"; then
    echo "✅ 检测到系统支持 IPv6"
    return 0
  elif ifconfig 2>/dev/null | grep -v "fe80:" | grep -q "inet6"; then
    echo "✅ 检测到系统支持 IPv6"
    return 0
  else
    echo "⚠️ 未检测到 IPv6 支持"
    return 1
  fi
}



# 配置 Docker 启用 IPv6
configure_docker_ipv6() {
  echo "🔧 配置 Docker IPv6 支持..."

  # 检查操作系统类型
  OS_TYPE=$(uname -s)

  if [[ "$OS_TYPE" == "Darwin" ]]; then
    # macOS 上 Docker Desktop 已默认支持 IPv6
    echo "✅ macOS Docker Desktop 默认支持 IPv6"
    return 0
  fi

  # Docker daemon 配置文件路径
  DOCKER_CONFIG="/etc/docker/daemon.json"

  # 检查是否需要 sudo
  if [[ $EUID -ne 0 ]]; then
    SUDO_CMD="sudo"
  else
    SUDO_CMD=""
  fi

  # 检查 Docker 配置文件
  if [ -f "$DOCKER_CONFIG" ]; then
    # 检查是否已经配置了 IPv6
    if grep -q '"ipv6"' "$DOCKER_CONFIG"; then
      echo "✅ Docker 已配置 IPv6 支持"
    else
      echo "📝 更新 Docker 配置以启用 IPv6..."
      # 备份原配置
      $SUDO_CMD cp "$DOCKER_CONFIG" "${DOCKER_CONFIG}.backup"

      # 使用 jq 或 sed 添加 IPv6 配置
      if command -v jq &> /dev/null; then
        $SUDO_CMD jq '. + {"ipv6": true, "fixed-cidr-v6": "fd00::/80"}' "$DOCKER_CONFIG" > /tmp/daemon.json && $SUDO_CMD mv /tmp/daemon.json "$DOCKER_CONFIG"
      else
        # 如果没有 jq，使用 sed
        $SUDO_CMD sed -i 's/^{$/{\n  "ipv6": true,\n  "fixed-cidr-v6": "fd00::\/80",/' "$DOCKER_CONFIG"
      fi

      echo "🔄 重启 Docker 服务..."
      if command -v systemctl &> /dev/null; then
        $SUDO_CMD systemctl restart docker
      elif command -v service &> /dev/null; then
        $SUDO_CMD service docker restart
      else
        echo "⚠️ 请手动重启 Docker 服务"
      fi
      sleep 5
    fi
  else
    # 创建新的配置文件
    echo "📝 创建 Docker 配置文件..."
    $SUDO_CMD mkdir -p /etc/docker
    echo '{
  "ipv6": true,
  "fixed-cidr-v6": "fd00::/80"
}' | $SUDO_CMD tee "$DOCKER_CONFIG" > /dev/null

    echo "🔄 重启 Docker 服务..."
    if command -v systemctl &> /dev/null; then
      $SUDO_CMD systemctl restart docker
    elif command -v service &> /dev/null; then
      $SUDO_CMD service docker restart
    else
      echo "⚠️ 请手动重启 Docker 服务"
    fi
    sleep 5
  fi
}

# 显示菜单
show_menu() {
  echo "==============================================="
  echo "          面板管理脚本"
  echo "==============================================="
  echo "请选择操作："
  echo "1. 安装面板"
  echo "2. 更新面板"
  echo "3. 卸载面板"
  echo "4. 退出"
  echo "==============================================="
}

generate_random() {
  LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c16
}

copy_runtime_file_if_missing() {
  local source_path="$1"
  local target_path="$2"
  if [[ -f "$source_path" && ! -f "$target_path" ]]; then
    cp "$source_path" "$target_path"
  fi
}

ensure_app_workdir() {
  mkdir -p "$PANEL_APP_DIR"
  copy_runtime_file_if_missing "$INITIAL_WORKDIR/.env" "$PANEL_ENV_FILE"
  copy_runtime_file_if_missing "$INITIAL_WORKDIR/docker-compose.yml" "$PANEL_COMPOSE_FILE"
}

download_compose_file() {
  local compose_url="$1"
  ensure_app_workdir
  curl -L -o "$PANEL_COMPOSE_FILE" "$compose_url"
}

run_compose() {
  ensure_app_workdir
  if [[ ! -f "$PANEL_COMPOSE_FILE" ]]; then
    echo "❌ 未找到面板配置文件：$PANEL_COMPOSE_FILE"
    return 1
  fi

  if [[ "$DOCKER_CMD" == "docker-compose" ]]; then
    COMPOSE_PROJECT_NAME="$PANEL_COMPOSE_PROJECT_NAME" docker-compose --project-directory "$PANEL_APP_DIR" -f "$PANEL_COMPOSE_FILE" "$@"
  else
    COMPOSE_PROJECT_NAME="$PANEL_COMPOSE_PROJECT_NAME" docker compose --project-directory "$PANEL_APP_DIR" -f "$PANEL_COMPOSE_FILE" "$@"
  fi
}

get_container_env_var() {
  local container_name="$1"
  local env_key="$2"
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container_name" 2>/dev/null | \
    awk -F= -v key="$env_key" '$1 == key { print substr($0, index($0, "=") + 1) }' | tail -n 1 || true
}

get_container_host_port() {
  local container_name="$1"
  local container_port="$2"
  docker port "$container_name" "$container_port" 2>/dev/null | head -n 1 | sed 's/.*://' || true
}

hydrate_env_from_running_containers() {
  FRONTEND_PORT=${FRONTEND_PORT:-$(get_container_host_port vite-frontend "80/tcp")}
  BACKEND_PORT=${BACKEND_PORT:-$(get_container_host_port springboot-backend "6365/tcp")}
  JWT_SECRET=${JWT_SECRET:-$(get_container_env_var springboot-backend "JWT_SECRET")}
  DB_HOST=${DB_HOST:-$(get_container_env_var springboot-backend "DB_HOST")}
  DB_PORT=${DB_PORT:-$(get_container_env_var springboot-backend "DB_PORT")}
  DB_NAME=${DB_NAME:-$(get_container_env_var "$POSTGRES_CONTAINER_NAME" "POSTGRES_DB")}
  DB_USER=${DB_USER:-$(get_container_env_var "$POSTGRES_CONTAINER_NAME" "POSTGRES_USER")}
  DB_PASSWORD=${DB_PASSWORD:-$(get_container_env_var "$POSTGRES_CONTAINER_NAME" "POSTGRES_PASSWORD")}
}

load_env_file() {
  ensure_app_workdir
  if [[ -f "$PANEL_ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$PANEL_ENV_FILE"
    set +a
  fi
  hydrate_env_from_running_containers
}

ensure_postgres_env() {
  DB_HOST=${DB_HOST:-postgres}
  DB_PORT=${DB_PORT:-5432}
  DB_NAME=${DB_NAME:-$DEFAULT_DB_NAME}
  DB_USER=${DB_USER:-$DEFAULT_DB_USER}
  DB_PASSWORD=${DB_PASSWORD:-$(generate_random)}
}

write_env_file() {
  ensure_app_workdir
  cat > "$PANEL_ENV_FILE" <<EOF
JWT_SECRET=$JWT_SECRET
FRONTEND_PORT=$FRONTEND_PORT
BACKEND_PORT=$BACKEND_PORT
DB_HOST=$DB_HOST
DB_PORT=$DB_PORT
DB_NAME=$DB_NAME
DB_USER=$DB_USER
DB_PASSWORD=$DB_PASSWORD
EOF
}

get_backend_image() {
  awk '
    /^  backend:$/ { in_backend=1; next }
    in_backend && /^  [A-Za-z0-9_-]+:$/ { in_backend=0 }
    in_backend && /^    image:/ { print $2; exit }
  ' "$PANEL_COMPOSE_FILE"
}

wait_for_postgres() {
  echo "🔍 检查 PostgreSQL 服务状态..."
  for i in {1..60}; do
    if docker ps --format "{{.Names}}" | grep -q "^${POSTGRES_CONTAINER_NAME}$"; then
      if docker exec "$POSTGRES_CONTAINER_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
        echo "✅ PostgreSQL 服务已就绪"
        return 0
      fi
    fi
    if [ $i -eq 60 ]; then
      echo "❌ PostgreSQL 启动超时（60秒）"
      return 1
    fi
    if [ $((i % 10)) -eq 1 ]; then
      echo "⏳ 等待 PostgreSQL 启动... ($i/60)"
    fi
    sleep 1
  done
}

wait_for_backend() {
  echo "🔍 检查后端服务状态..."
  for i in {1..90}; do
    if docker ps --format "{{.Names}}" | grep -q "^springboot-backend$"; then
      BACKEND_HEALTH=$(docker inspect -f '{{.State.Health.Status}}' springboot-backend 2>/dev/null || echo "unknown")
      if [[ "$BACKEND_HEALTH" == "healthy" ]]; then
        echo "✅ 后端服务健康检查通过"
        return 0
      fi
      if [[ "$BACKEND_HEALTH" == "unhealthy" ]]; then
        echo "⚠️ 后端健康状态：$BACKEND_HEALTH"
      fi
    else
      BACKEND_HEALTH="not_running"
    fi
    if [ $i -eq 90 ]; then
      echo "❌ 后端服务启动超时（90秒）"
      echo "🔍 当前状态：$(docker inspect -f '{{.State.Health.Status}}' springboot-backend 2>/dev/null || echo '容器不存在')"
      return 1
    fi
    if [ $((i % 15)) -eq 1 ]; then
      echo "⏳ 等待后端服务启动... ($i/90) 状态：${BACKEND_HEALTH:-unknown}"
    fi
    sleep 1
  done
}

run_sqlite_to_postgres_migration() {
  if ! docker volume inspect "$SQLITE_VOLUME_NAME" >/dev/null 2>&1; then
    echo "ℹ️ 未检测到旧版 sqlite_data 卷，跳过历史数据迁移"
    return 0
  fi

  local backend_image
  backend_image=$(get_backend_image)
  if [[ -z "$backend_image" ]]; then
    echo "❌ 无法从 docker-compose.yml 解析后端镜像"
    return 1
  fi

  if ! docker run --rm --entrypoint sh -v "${SQLITE_VOLUME_NAME}:/sqlite-import:ro" "$backend_image" -c 'test -f /sqlite-import/gost.db' >/dev/null 2>&1; then
    echo "ℹ️ sqlite_data 卷中未找到 gost.db，跳过历史数据迁移"
    return 0
  fi

  echo "🔄 执行 SQLite -> PostgreSQL 数据迁移..."
  local migration_status=0
  docker run --rm \
    --network gost-network \
    --env-file "$PANEL_ENV_FILE" \
    -e DB_HOST=postgres \
    -e DB_PORT="$DB_PORT" \
    -e DB_NAME="$DB_NAME" \
    -e DB_USER="$DB_USER" \
    -e DB_PASSWORD="$DB_PASSWORD" \
    -e JWT_SECRET="$JWT_SECRET" \
    -e LOG_DIR=/app/logs \
    -e APP_MIGRATION_MODE=sqlite-to-postgres \
    -e SQLITE_IMPORT_PATH=/sqlite-import/gost.db \
    -e APP_MIGRATION_EXIT_AFTER_RUN=true \
    -v "${SQLITE_VOLUME_NAME}:/sqlite-import:ro" \
    -v "${BACKEND_LOGS_VOLUME_NAME}:/app/logs" \
    "$backend_image" || migration_status=$?
  if [[ $migration_status -ne 0 ]]; then
    echo "❌ SQLite -> PostgreSQL 数据迁移失败，退出码: $migration_status"
    return $migration_status
  fi
  echo "✅ SQLite -> PostgreSQL 迁移完成"
}

# 删除脚本自身
delete_self() {
  echo ""
  echo "🗑️ 操作已完成，正在清理脚本文件..."
  SCRIPT_PATH="$(readlink -f "$0" 2>/dev/null || realpath "$0" 2>/dev/null || echo "$0")"
  sleep 1
  rm -f "$SCRIPT_PATH" && echo "✅ 脚本文件已删除" || echo "❌ 删除脚本文件失败"
}



# 获取用户输入的配置参数
get_config_params() {
  echo "🔧 请输入配置参数："

  read -p "前端端口（默认 6366）: " FRONTEND_PORT
  FRONTEND_PORT=${FRONTEND_PORT:-6366}

  read -p "后端端口（默认 6365）: " BACKEND_PORT
  BACKEND_PORT=${BACKEND_PORT:-6365}

  # 生成JWT密钥
  JWT_SECRET=$(generate_random)
  ensure_postgres_env
}

# 安装功能
install_panel() {
  echo "🚀 开始安装面板..."
  check_docker
  ensure_app_workdir
  get_config_params
  echo "📁 面板工作目录：$PANEL_APP_DIR"

  echo "🔽 下载必要文件..."
  DOCKER_COMPOSE_URL=$(get_docker_compose_url)
  echo "📡 选择配置文件：$(basename "$DOCKER_COMPOSE_URL")"
  download_compose_file "$DOCKER_COMPOSE_URL"
  echo "✅ 文件准备完成"

  # 自动检测并配置 IPv6 支持
  if check_ipv6_support; then
    echo "🚀 系统支持 IPv6，自动启用 IPv6 配置..."
    configure_docker_ipv6
  fi

  write_env_file

  echo "🚀 启动 docker 服务..."
  run_compose up -d

  wait_for_postgres
  wait_for_backend

  echo "🎉 部署完成"
  echo "🌐 访问地址: http://服务器IP:$FRONTEND_PORT"
  echo "📖 部署完成后请阅读下使用文档，求求了啊，不要上去就是一顿操作"
  echo "📚 文档地址: https://tes.cc/guide.html"
  echo "💡 默认管理员账号: admin_user / admin_user"
  echo "⚠️  登录后请立即修改默认密码！"


}

# 更新功能
update_panel() {
  echo "🔄 开始更新面板..."
  check_docker
  ensure_app_workdir
  load_env_file
  FRONTEND_PORT=${FRONTEND_PORT:-6366}
  BACKEND_PORT=${BACKEND_PORT:-6365}
  JWT_SECRET=${JWT_SECRET:-$(generate_random)}
  ensure_postgres_env
  echo "📁 面板工作目录：$PANEL_APP_DIR"

  echo "🔽 下载最新配置文件..."
  DOCKER_COMPOSE_URL=$(get_docker_compose_url)
  echo "📡 选择配置文件：$(basename "$DOCKER_COMPOSE_URL")"
  download_compose_file "$DOCKER_COMPOSE_URL"
  echo "✅ 下载完成"

  # 自动检测并配置 IPv6 支持
  if check_ipv6_support; then
    echo "🚀 系统支持 IPv6，自动启用 IPv6 配置..."
    configure_docker_ipv6
  fi

  write_env_file

  echo "⬇️ 拉取最新镜像..."
  run_compose pull

  # 先发送 SIGTERM 信号，让应用优雅关闭
  docker stop -t 30 springboot-backend 2>/dev/null || true
  docker stop -t 10 vite-frontend 2>/dev/null || true
  
  echo "⏳ 等待旧版 SQLite 数据同步..."
  sleep 5

  echo "🚀 启动 PostgreSQL 服务..."
  run_compose up -d postgres
  wait_for_postgres

  if ! run_sqlite_to_postgres_migration; then
    echo "❌ 数据迁移失败，已终止升级。原 sqlite_data 卷已保留，可用于回滚。"
    return 1
  fi

  echo "🚀 启动更新后的服务..."
  run_compose up -d backend frontend

  # 等待服务启动
  echo "⏳ 等待服务启动..."
  wait_for_backend || {
    echo "🛑 更新终止"
    return 1
  }

  echo "✅ 更新完成"
}



# 卸载功能
uninstall_panel() {
  echo "🗑️ 开始卸载面板..."
  check_docker
  ensure_app_workdir
  echo "📁 面板工作目录：$PANEL_APP_DIR"

  if [[ ! -f "$PANEL_COMPOSE_FILE" ]]; then
    echo "⚠️ 未找到 docker-compose.yml 文件，正在下载以完成卸载..."
    DOCKER_COMPOSE_URL=$(get_docker_compose_url)
    echo "📡 选择配置文件：$(basename "$DOCKER_COMPOSE_URL")"
    download_compose_file "$DOCKER_COMPOSE_URL"
    echo "✅ docker-compose.yml 下载完成"
  fi

  read -p "确认卸载面板吗？此操作将停止并删除所有容器和数据 (y/N): " confirm
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    echo "❌ 取消卸载"
    return 0
  fi

  echo "🛑 停止并删除容器、镜像、卷..."
  run_compose down --rmi all --volumes --remove-orphans
  docker volume rm -f sqlite_data postgres_data backend_logs >/dev/null 2>&1 || true
  echo "🧹 删除配置文件..."
  rm -f "$PANEL_COMPOSE_FILE" "$PANEL_ENV_FILE"
  echo "✅ 卸载完成"
}

# 主逻辑
main() {

  # 显示交互式菜单
  while true; do
    show_menu
    read -p "请输入选项 (1-5): " choice

    case $choice in
      1)
        install_panel
        delete_self
        exit 0
        ;;
      2)
        update_panel
        delete_self
        exit 0
        ;;
      3)
        uninstall_panel
        delete_self
        exit 0
        ;;
      4)
        echo "👋 退出脚本"
        delete_self
        exit 0
        ;;
      *)
        echo "❌ 无效选项，请输入 1-5"
        echo ""
        ;;
    esac
  done
}

# 执行主函数
main
