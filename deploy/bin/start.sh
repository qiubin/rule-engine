#!/bin/bash
set -e

APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"
JAR_NAME="rule-engine-server-1.0.0-SNAPSHOT.jar"
JAR_PATH="$APP_HOME/$JAR_NAME"
LOG_FILE="$APP_HOME/logs/app.log"
PID_FILE="$APP_HOME/bin/app.pid"

# 加载环境变量
if [ -f "$APP_HOME/config/env.sh" ]; then
    source "$APP_HOME/config/env.sh"
fi

# JVM 参数
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC -Dfile.encoding=UTF-8}"

# Spring 启动参数
SPRING_OPTS="--spring.profiles.active=prod \
    --server.port=${SERVER_PORT:-8082} \
    --spring.datasource.url=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}?useSSL=false\&serverTimezone=Asia/Shanghai\&allowPublicKeyRetrieval=true\&characterEncoding=utf8 \
    --spring.datasource.username=${MYSQL_USER} \
    --spring.datasource.password=${MYSQL_PASSWORD}"

if [ ! -f "$JAR_PATH" ]; then
    echo "错误: 未找到 $JAR_PATH"
    exit 1
fi

mkdir -p "$APP_HOME/logs"

if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
    echo "应用已在运行 (PID: $(cat $PID_FILE))"
    exit 0
fi

echo "正在启动规则引擎..."
nohup java $JAVA_OPTS -jar "$JAR_PATH" $SPRING_OPTS > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

sleep 2
if kill -0 $(cat "$PID_FILE") 2>/dev/null; then
    echo "启动成功 (PID: $(cat $PID_FILE))"
    echo "日志: $LOG_FILE"
    echo "API 地址: http://localhost:${SERVER_PORT:-8082}"
else
    echo "启动失败，请检查日志"
    exit 1
fi
