#!/bin/bash

DEFAULT_SERVICE_NAME="flux_agent"
SERVICE_NAME=""
SERVER_ADDR=""
SECRET=""
ACTION=""

get_architecture() {
    ARCH=$(uname -m)
    case "$ARCH" in
        x86_64)
            echo "amd64"
            ;;
        aarch64|arm64)
            echo "arm64"
            ;;
        *)
            echo "amd64"
            ;;
    esac
}

build_download_url() {
    local arch
    arch=$(get_architecture)
    echo "https://github.com/aict666/flux-panel/releases/download/2.0.7-beta/gost-${arch}"
}

DOWNLOAD_URL=$(build_download_url)
COUNTRY=$(curl -s https://ipinfo.io/country)
if [ "$COUNTRY" = "CN" ]; then
    DOWNLOAD_URL="https://ghfast.top/${DOWNLOAD_URL}"
fi

refresh_paths() {
  SERVICE_NAME="${SERVICE_NAME:-$DEFAULT_SERVICE_NAME}"
  INSTALL_DIR="/etc/${SERVICE_NAME}"
  BINARY_PATH="${INSTALL_DIR}/flux_agent"
  CONFIG_FILE="${INSTALL_DIR}/config.json"
  GOST_CONFIG="${INSTALL_DIR}/gost.json"
  SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
}

show_menu() {
  echo "==============================================="
  echo "              管理脚本"
  echo "==============================================="
  echo "当前服务名: ${SERVICE_NAME:-$DEFAULT_SERVICE_NAME}"
  echo "请选择操作："
  echo "1. 安装"
  echo "2. 更新"
  echo "3. 卸载"
  echo "4. 退出"
  echo "==============================================="
}

delete_self() {
  echo ""
  echo "🗑️ 操作已完成，正在清理脚本文件..."
  SCRIPT_PATH="$(readlink -f "$0" 2>/dev/null || realpath "$0" 2>/dev/null || echo "$0")"
  sleep 1
  rm -f "$SCRIPT_PATH" && echo "✅ 脚本文件已删除" || echo "❌ 删除脚本文件失败"
}

validate_service_name() {
  if [[ ! "$SERVICE_NAME" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$ ]]; then
    echo "❌ 安装服务名不合法，仅允许字母、数字、点、下划线和中划线。"
    exit 1
  fi
}

ensure_service_name() {
  if [[ -z "$SERVICE_NAME" ]]; then
    read -p "安装服务名 [${DEFAULT_SERVICE_NAME}]: " SERVICE_NAME
  fi
  SERVICE_NAME="${SERVICE_NAME:-$DEFAULT_SERVICE_NAME}"
  validate_service_name
  refresh_paths
}

check_and_install_tcpkill() {
  if command -v tcpkill &> /dev/null; then
    return 0
  fi

  OS_TYPE=$(uname -s)
  if [[ $EUID -ne 0 ]]; then
    SUDO_CMD="sudo"
  else
    SUDO_CMD=""
  fi

  if [[ "$OS_TYPE" == "Darwin" ]]; then
    if command -v brew &> /dev/null; then
      brew install dsniff &> /dev/null
    fi
    return 0
  fi

  if [ -f /etc/os-release ]; then
    . /etc/os-release
    DISTRO=$ID
  elif [ -f /etc/redhat-release ]; then
    DISTRO="rhel"
  elif [ -f /etc/debian_version ]; then
    DISTRO="debian"
  else
    return 0
  fi

  case $DISTRO in
    ubuntu|debian)
      $SUDO_CMD apt update &> /dev/null
      $SUDO_CMD apt install -y dsniff &> /dev/null
      ;;
    centos|rhel|fedora)
      if command -v dnf &> /dev/null; then
        $SUDO_CMD dnf install -y dsniff &> /dev/null
      elif command -v yum &> /dev/null; then
        $SUDO_CMD yum install -y dsniff &> /dev/null
      fi
      ;;
    alpine)
      $SUDO_CMD apk add --no-cache dsniff &> /dev/null
      ;;
    arch|manjaro)
      $SUDO_CMD pacman -S --noconfirm dsniff &> /dev/null
      ;;
    opensuse*|sles)
      $SUDO_CMD zypper install -y dsniff &> /dev/null
      ;;
    gentoo)
      $SUDO_CMD emerge --ask=n net-analyzer/dsniff &> /dev/null
      ;;
    void)
      $SUDO_CMD xbps-install -Sy dsniff &> /dev/null
      ;;
  esac
}

get_config_params() {
  if [[ -z "$SERVER_ADDR" || -z "$SECRET" ]]; then
    echo "请输入配置参数："

    if [[ -z "$SERVER_ADDR" ]]; then
      read -p "服务器地址: " SERVER_ADDR
    fi

    if [[ -z "$SECRET" ]]; then
      read -p "密钥: " SECRET
    fi

    if [[ -z "$SERVER_ADDR" || -z "$SECRET" ]]; then
      echo "❌ 参数不完整，操作取消。"
      exit 1
    fi
  fi
}

service_exists() {
  [[ -f "$SERVICE_FILE" ]] || systemctl list-units --full -all 2>/dev/null | grep -Fq "${SERVICE_NAME}.service"
}

stop_and_disable_service() {
  if service_exists; then
    systemctl stop "$SERVICE_NAME" 2>/dev/null && echo "🛑 停止服务 ${SERVICE_NAME}"
    systemctl disable "$SERVICE_NAME" 2>/dev/null && echo "🚫 禁用自启 ${SERVICE_NAME}"
  fi
}

write_service_file() {
  cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=${SERVICE_NAME} Proxy Service
After=network.target

[Service]
WorkingDirectory=${INSTALL_DIR}
ExecStart=${BINARY_PATH}
Restart=on-failure

[Install]
WantedBy=multi-user.target
EOF
}

install_flux_agent() {
  ensure_service_name
  echo "🚀 开始安装 ${SERVICE_NAME}..."
  get_config_params
  check_and_install_tcpkill

  mkdir -p "$INSTALL_DIR"
  stop_and_disable_service

  if [[ -f "$BINARY_PATH" ]]; then
    echo "🧹 删除旧文件 ${BINARY_PATH}"
    rm -f "$BINARY_PATH"
  fi

  echo "⬇️ 下载 ${SERVICE_NAME} 中..."
  curl -L "$DOWNLOAD_URL" -o "$BINARY_PATH"
  if [[ ! -f "$BINARY_PATH" || ! -s "$BINARY_PATH" ]]; then
    echo "❌ 下载失败，请检查网络或下载链接。"
    exit 1
  fi
  chmod +x "$BINARY_PATH"
  echo "✅ 下载完成"
  echo "🔎 ${SERVICE_NAME} 版本：$($BINARY_PATH -V)"

  echo "📄 创建新配置: config.json"
  cat > "$CONFIG_FILE" <<EOF
{
  "addr": "$SERVER_ADDR",
  "secret": "$SECRET"
}
EOF

  if [[ -f "$GOST_CONFIG" ]]; then
    echo "⏭️ 跳过配置文件: gost.json (已存在)"
  else
    echo "📄 创建新配置: gost.json"
    cat > "$GOST_CONFIG" <<EOF
{}
EOF
  fi

  chmod 600 "$INSTALL_DIR"/*.json
  write_service_file

  systemctl daemon-reload
  systemctl enable "$SERVICE_NAME"
  systemctl start "$SERVICE_NAME"

  echo "🔄 检查服务状态..."
  if systemctl is-active --quiet "$SERVICE_NAME"; then
    echo "✅ 安装完成，${SERVICE_NAME} 已启动并设置为开机启动。"
    echo "📁 配置目录: ${INSTALL_DIR}"
    echo "📄 服务文件: ${SERVICE_FILE}"
    echo "🔧 服务状态: $(systemctl is-active "$SERVICE_NAME")"
  else
    echo "❌ ${SERVICE_NAME} 启动失败，请执行以下命令查看日志："
    echo "journalctl -u ${SERVICE_NAME} -f"
  fi
}

update_flux_agent() {
  ensure_service_name
  echo "🔄 开始更新 ${SERVICE_NAME}..."

  if [[ ! -d "$INSTALL_DIR" || ! -f "$BINARY_PATH" || ! -f "$SERVICE_FILE" ]]; then
    echo "❌ 未找到 ${SERVICE_NAME} 对应的安装实例。"
    return 1
  fi

  echo "📥 使用下载地址: $DOWNLOAD_URL"
  check_and_install_tcpkill

  echo "⬇️ 下载最新版本..."
  curl -L "$DOWNLOAD_URL" -o "${BINARY_PATH}.new"
  if [[ ! -f "${BINARY_PATH}.new" || ! -s "${BINARY_PATH}.new" ]]; then
    echo "❌ 下载失败。"
    return 1
  fi

  if service_exists; then
    echo "🛑 停止 ${SERVICE_NAME} 服务..."
    systemctl stop "$SERVICE_NAME"
  fi

  mv "${BINARY_PATH}.new" "$BINARY_PATH"
  chmod +x "$BINARY_PATH"
  echo "🔎 新版本：$($BINARY_PATH -V)"

  echo "🔄 重启服务..."
  systemctl start "$SERVICE_NAME"
  echo "✅ 更新完成，${SERVICE_NAME} 已重新启动。"
}

uninstall_flux_agent() {
  ensure_service_name
  echo "🗑️ 开始卸载 ${SERVICE_NAME}..."
  echo "即将删除："
  echo "- 服务文件: ${SERVICE_FILE}"
  echo "- 安装目录: ${INSTALL_DIR}"
  read -p "确认卸载 ${SERVICE_NAME} 吗？此操作将删除以上文件 (y/N): " confirm
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    echo "❌ 取消卸载"
    return 0
  fi

  stop_and_disable_service

  if [[ -f "$SERVICE_FILE" ]]; then
    rm -f "$SERVICE_FILE"
    echo "🧹 删除服务文件"
  fi

  if [[ -d "$INSTALL_DIR" ]]; then
    rm -rf "$INSTALL_DIR"
    echo "🧹 删除安装目录: ${INSTALL_DIR}"
  fi

  systemctl daemon-reload
  echo "✅ 卸载完成"
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -a)
        SERVER_ADDR="$2"
        shift 2
        ;;
      -s)
        SECRET="$2"
        shift 2
        ;;
      -n)
        SERVICE_NAME="$2"
        shift 2
        ;;
      --update)
        ACTION="update"
        shift
        ;;
      --uninstall)
        ACTION="uninstall"
        shift
        ;;
      --install)
        ACTION="install"
        shift
        ;;
      *)
        echo "❌ 无效参数: $1"
        exit 1
        ;;
    esac
  done
}

main() {
  parse_args "$@"

  if [[ -z "$ACTION" && ( -n "$SERVER_ADDR" || -n "$SECRET" ) ]]; then
    ACTION="install"
  fi

  case "$ACTION" in
    install)
      install_flux_agent
      delete_self
      exit 0
      ;;
    update)
      update_flux_agent
      delete_self
      exit 0
      ;;
    uninstall)
      uninstall_flux_agent
      delete_self
      exit 0
      ;;
  esac

  while true; do
    ensure_service_name
    show_menu
    read -p "请输入选项 (1-4): " choice

    case $choice in
      1)
        install_flux_agent
        delete_self
        exit 0
        ;;
      2)
        update_flux_agent
        delete_self
        exit 0
        ;;
      3)
        uninstall_flux_agent
        delete_self
        exit 0
        ;;
      4)
        echo "👋 退出脚本"
        delete_self
        exit 0
        ;;
      *)
        echo "❌ 无效选项，请输入 1-4"
        echo ""
        ;;
    esac
  done
}

main "$@"
