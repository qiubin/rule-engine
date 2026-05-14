#!/bin/bash
# 数据库配置（修改为你的实际值）
export MYSQL_HOST="localhost"
export MYSQL_PORT="3306"
export MYSQL_DB="ruleengine"
export MYSQL_USER="root"
export MYSQL_PASSWORD="your_password"

# 服务端口
export SERVER_PORT="8082"

# JVM 参数
export JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Dfile.encoding=UTF-8"
