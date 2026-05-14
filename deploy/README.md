# 规则引擎部署指南

## 一、服务器环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| **Linux** | CentOS 7+/Ubuntu 18+ | 服务器操作系统 |
| **Java** | 8+ | `java -version` 确认 |
| **MySQL** | 8.0+ | 已有数据库，无需额外安装 |

## 二、部署包结构

```
deploy/
├── rule-engine-server-1.0.0-SNAPSHOT.jar   # 后端可执行 JAR
├── frontend/                                # 前端静态文件（dist）
├── bin/
│   ├── start.sh                             # 启动脚本
│   └── stop.sh                              # 停止脚本
├── config/
│   ├── env.sh                               # 环境变量（数据库配置）
│   └── rule-engine.service                  # systemd 服务文件
└── deploy.sh                                # 一键部署脚本（服务器端执行）
```

## 三、快速部署（推荐）

### 步骤 1：上传部署包到服务器

```bash
# 本机执行，将 deploy/ 目录压缩上传
zip -r deploy.zip deploy/
# 通过 scp 上传到服务器
scp deploy.zip user@服务器IP:/tmp/
```

### 步骤 2：服务器端执行部署

```bash
# 登录服务器
ssh user@服务器IP

# 解压
cd /tmp
unzip deploy.zip
cd deploy

# 执行部署脚本（需要 sudo 权限安装 systemd 服务）
chmod +x deploy.sh
sudo ./deploy.sh
```

### 步骤 3：修改数据库配置

```bash
# 编辑环境配置文件
sudo vi /opt/rule-engine/config/env.sh
```

修改以下参数为你的实际值：

```bash
export MYSQL_HOST="localhost"          # MySQL 地址
export MYSQL_PORT="3306"               # MySQL 端口
export MYSQL_DB="ruleengine"           # 数据库名
export MYSQL_USER="root"               # 用户名
export MYSQL_PASSWORD="your_password"  # 密码 ← 必须修改
export SERVER_PORT="8082"              # 服务端口
```

### 步骤 4：创建数据库

如果数据库 `ruleengine` 不存在，先创建：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ruleengine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 步骤 5：启动服务

**方式 A：直接启动（测试用）**

```bash
/opt/rule-engine/bin/start.sh
```

**方式 B：systemd 管理（生产推荐）**

```bash
sudo systemctl start rule-engine    # 启动
sudo systemctl stop rule-engine     # 停止
sudo systemctl status rule-engine   # 查看状态
sudo systemctl enable rule-engine   # 开机自启
```

### 步骤 6：访问应用

```
后端 API: http://服务器IP:8082
前端页面: http://服务器IP:3001（见下方前端部署方式）
```

## 四、前端部署方式

由于你选择 **IP 直接访问、内网使用**，前端有以下几种部署方式：

### 方式 1：Python 临时服务（最快，测试用）

```bash
cd /opt/rule-engine/frontend
python3 -m http.server 3001
# 访问: http://服务器IP:3001
```

### 方式 2：Nginx 托管（生产推荐）

```bash
# 安装 Nginx
sudo apt install nginx     # Ubuntu
sudo yum install nginx     # CentOS

# 配置 Nginx
sudo vi /etc/nginx/conf.d/rule-engine.conf
```

写入以下内容：

```nginx
server {
    listen 80;
    server_name 服务器IP;

    # 前端静态文件
    location / {
        root /opt/rule-engine/frontend;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://localhost:8082;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

```bash
sudo nginx -t
sudo systemctl reload nginx
# 访问: http://服务器IP
```

## 五、常见问题

### 1. 启动失败，日志提示数据库连接错误

检查 `config/env.sh` 中的数据库配置是否正确，确认 MySQL 服务已启动。

### 2. 端口被占用

```bash
# 检查 8082 端口占用
sudo lsof -i:8082
# 修改端口：编辑 config/env.sh 中的 SERVER_PORT
```

### 3. 如何更新部署

```bash
# 1. 停止服务
sudo systemctl stop rule-engine

# 2. 上传新的 JAR 文件覆盖旧的
# 3. 重新启动
sudo systemctl start rule-engine
```

### 4. 日志查看

```bash
# 应用日志
tail -f /opt/rule-engine/logs/app.log

# systemd 日志
sudo journalctl -u rule-engine -f
```

## 六、防火墙配置（如需外部访问）

```bash
# Ubuntu (UFW)
sudo ufw allow 8082/tcp
sudo ufw allow 80/tcp

# CentOS (firewalld)
sudo firewall-cmd --permanent --add-port=8082/tcp
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --reload
```
