#!/bin/bash

# 规则引擎启动脚本
# 使用方式: ./start.sh [backend|frontend|all]

set -e

RULE_ENGINE_DIR="$(cd "$(dirname "$0")" && pwd)"
SOCAT_PID=""

cleanup() {
    if [ -n "$SOCAT_PID" ] && kill -0 "$SOCAT_PID" 2>/dev/null; then
        kill "$SOCAT_PID" 2>/dev/null
        echo "[socat] 代理已停止"
    fi
}
trap cleanup EXIT

start_backend() {
    echo "========================================"
    echo "正在启动后端服务..."
    echo "========================================"

    # 启动 socat TCP 代理（绕开网络设备对 Java 直连 MySQL 的拦截）
    MYSQL_REMOTE_HOST="${MYSQL_REMOTE_HOST:-192.168.2.166}"
    MYSQL_REMOTE_PORT="${MYSQL_REMOTE_PORT:-3306}"
    SOCAT_PORT="${SOCAT_PORT:-13306}"
    if command -v socat &> /dev/null; then
        if lsof -ti:"$SOCAT_PORT" &> /dev/null; then
            echo "[socat] 代理已在运行 (localhost:$SOCAT_PORT → $MYSQL_REMOTE_HOST:$MYSQL_REMOTE_PORT)"
        else
            socat TCP-LISTEN:"$SOCAT_PORT",fork,reuseaddr TCP:"$MYSQL_REMOTE_HOST":"$MYSQL_REMOTE_PORT" &
            SOCAT_PID=$!
            echo "[socat] 代理已启动 (localhost:$SOCAT_PORT → $MYSQL_REMOTE_HOST:$MYSQL_REMOTE_PORT) PID=$SOCAT_PID"
        fi
    else
        echo "[socat] 未安装，尝试安装..."
        brew install socat 2>/dev/null && echo "[socat] 安装成功" || echo "[socat] 请手动执行: brew install socat"
    fi

    cd "$RULE_ENGINE_DIR/rule-engine-server"

    MVN_CMD="mvn"
    if ! command -v mvn &> /dev/null; then
        if [ -f "./mvnw" ]; then
            MVN_CMD="./mvnw"
        else
            echo "错误: 未找到 Maven 且无 mvnw 脚本，请先安装 Maven 3.9+"
            exit 1
        fi
    fi

    if ! command -v java &> /dev/null; then
        echo "错误: 未找到 Java，请先安装 Java 8+"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    echo "Java 版本: $JAVA_VERSION"

    $MVN_CMD clean install -DskipTests
    MYSQL_HOST=localhost MYSQL_PORT="$SOCAT_PORT" $MVN_CMD spring-boot:run &
    echo "后端服务已启动在 http://localhost:8081"
}

start_frontend() {
    echo "========================================"
    echo "正在启动前端服务..."
    echo "========================================"
    cd "$RULE_ENGINE_DIR/rule-engine-ui"
    
    export NVM_DIR="$HOME/.nvm"
    [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"

    if ! command -v npm &> /dev/null; then
        echo "错误: 未找到 npm，请先安装 Node.js 18+"
        exit 1
    fi
    
    NODE_VERSION=$(node -v)
    echo "Node 版本: $NODE_VERSION"
    
    if [ ! -d "node_modules" ]; then
        echo "正在安装前端依赖..."
        npm install
    fi
    
    npm run dev &
    echo "前端服务已启动在 http://localhost:3000"
}

case "${1:-all}" in
    backend)
        start_backend
        ;;
    frontend)
        start_frontend
        ;;
    all)
        start_backend
        sleep 10
        start_frontend
        echo ""
        echo "========================================"
        echo "所有服务已启动!"
        echo "前端: http://localhost:3000"
        echo "后端: http://localhost:8081"
        echo "========================================"
        ;;
    *)
        echo "使用方式: $0 [backend|frontend|all]"
        exit 1
        ;;
esac

wait
