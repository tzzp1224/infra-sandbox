# 课题1：Spring Boot 3 + Redis 最小 REST API 实验手册

这个 Demo 的业务场景是：**AI Agent 的会话记忆管理器**。

你会通过一个接口 `POST /api/chat`：
1. 按 `userId` 读取历史消息（Redis List）
2. 把新消息追加到历史末尾
3. 把过期时间（TTL）刷新为 60 秒
4. 返回最新完整历史

---

## 1. 你将学到什么

- Spring Boot 如何快速暴露 REST API
- Redis 的 List 结构如何保存对话历史
- TTL（过期时间）如何实现“短时记忆”
- Docker Compose 如何一键启动 Redis

---

## 2. 项目结构速览

```text
topic1-springboot3-redis-chat-memory/
├── docker-compose.yml
├── pom.xml
└── src/main
    ├── java/com/demo/memory
    │   ├── MemoryDemoApplication.java
    │   ├── RedisConfig.java
    │   └── ChatController.java
    └── resources/application.yml
```

---

## 3. 实验前准备（先检查环境）

在项目目录执行：

```bash
cd /Users/dexter/Documents/Dexter_Work/infra-sandbox/topic1-springboot3-redis-chat-memory

java -version
mvn -v
docker -v
docker compose version
```

### 期待现象

- `java -version` 显示 JDK 17（或更高，建议 17）
- `mvn -v` 能显示 Maven 版本
- `docker -v` 和 `docker compose version` 能正常输出版本

### 说明了什么

- Java + Maven 负责构建并运行 Spring Boot
- Docker Compose 负责拉起 Redis 容器

> 如果 `mvn` 提示 `command not found`，先安装 Maven（例如 macOS: `brew install maven`）。

---

## 4. 实验步骤（命令 + 现象 + 含义 + 代码对应）

## Step 1：启动 Redis 容器

```bash
docker compose up -d
docker compose ps
docker exec topic1-redis redis-cli ping
```

### 期待现象

- `docker compose ps` 显示 `topic1-redis` 为 `running`
- `redis-cli ping` 返回 `PONG`

### 说明了什么

- Redis 服务可用，应用后续可以通过 `localhost:6379` 连接

### 对应代码

- `docker-compose.yml`：定义 Redis 镜像与端口映射
- `application.yml`：`spring.data.redis.host=localhost`，`port=6379`

---

## Step 2：启动 Spring Boot 应用

```bash
mvn spring-boot:run
```

### 期待现象

日志中可看到类似信息：
- `Tomcat started on port 8080`
- `Started MemoryDemoApplication`

### 说明了什么

- Spring Boot 已成功启动 Web 服务
- 现在可以访问 `http://localhost:8080`

### 对应代码

- `MemoryDemoApplication.java`：应用入口
- `pom.xml`：`spring-boot-starter-web` + `spring-boot-starter-data-redis`

---

## Step 3：第一次调用聊天接口

新开一个终端执行：

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId":"u1","message":"你好"}'
```

### 期待现象

返回类似：

```json
{"userId":"u1","history":["你好"]}
```

### 说明了什么

- 对于 `u1`，原先没有历史，Redis 新建 List 并插入第一条消息

### 对应代码（核心）

- `ChatController.java:35`：`LRANGE` 读取历史
- `ChatController.java:39`：`RPUSH` 追加消息
- `ChatController.java:43`：`EXPIRE 60` 刷新 TTL
- `ChatController.java:47`：再次 `LRANGE` 返回最新历史

---

## Step 4：同一用户再次发消息（验证追加）

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId":"u1","message":"我在学习Redis"}'
```

### 期待现象

返回类似：

```json
{"userId":"u1","history":["你好","我在学习Redis"]}
```

### 说明了什么

- 同一个 `userId` 的历史被按顺序追加（List 尾插）

### 对应代码

- `ChatController.java:39` 的 `rightPush` 决定了“按时间顺序追加到尾部”

---

## Step 5：换一个用户（验证数据隔离）

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId":"u2","message":"我是另一个用户"}'
```

### 期待现象

返回类似：

```json
{"userId":"u2","history":["我是另一个用户"]}
```

### 说明了什么

- 不同用户使用不同 Redis Key（`chat:history:u1`、`chat:history:u2`）
- 各自历史互不影响

### 对应代码

- `ChatController.java:27`：`String key = "chat:history:" + request.userId();`

---

## Step 6：直接观察 Redis 内部数据

```bash
docker exec topic1-redis redis-cli LRANGE chat:history:u1 0 -1
docker exec topic1-redis redis-cli TTL chat:history:u1
```

### 期待现象

- `LRANGE` 输出 `u1` 的全部历史
- `TTL` 输出一个接近 60 的倒计时秒数（如 57、52）

### 说明了什么

- 应用确实把对话放在 Redis List 中
- 记忆是“有生命周期”的，不是永久存储

### 对应代码

- `ChatController.java:35` / `:47`：`range`
- `ChatController.java:43`：`expire(60s)`

---

## Step 7：验证“滑动过期”机制（重点）

按顺序执行：

```bash
# 先发一条，建立/刷新 TTL
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId":"u1","message":"刷新TTL测试-1"}'

# 看当前 TTL（应接近 60）
docker exec topic1-redis redis-cli TTL chat:history:u1

# 等 20 秒后再发一条
sleep 20
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId":"u1","message":"刷新TTL测试-2"}'

# 再看 TTL（应重新接近 60，而不是接近 40）
docker exec topic1-redis redis-cli TTL chat:history:u1

# 停止发送，等待超过 60 秒
sleep 65

# 再查 TTL（通常会是 -2，表示 key 已过期删除）
docker exec topic1-redis redis-cli TTL chat:history:u1
```

### 期待现象

- 第二次发消息后 TTL 被“拉回”接近 60
- 长时间不发后，TTL 为 `-2`

### 说明了什么

- 该方案实现了会话“短时记忆”
- 只要用户持续对话，记忆就继续保留；沉默超过 60 秒自动遗忘

### 对应代码

- `ChatController.java:43`：每次请求都执行 `expire(key, 60s)`

---

## Step 8：停机清理

```bash
# 停止 Spring Boot：在运行 mvn 的终端按 Ctrl + C

docker compose down
```

### 期待现象

- Redis 容器退出并移除

### 说明了什么

- 实验环境已回收，下次可重新开始

---

## 5. 快速看懂代码（10 分钟路线）

按这个顺序读：

1. `pom.xml`
- 看依赖：
  - `spring-boot-starter-web`（提供 REST API 能力）
  - `spring-boot-starter-data-redis`（提供 RedisTemplate）

2. `application.yml`
- 看 Redis 连接地址（`localhost:6379`）和服务端口（`8080`）

3. `RedisConfig.java`
- 看 `RedisTemplate<String, String>` Bean
- 看 String 序列化器，确保 key/value 以可读字符串存入 Redis

4. `ChatController.java`（最重要）
- `:27` 生成用户 key
- `:31` 获取 List 操作句柄
- `:35` 读历史（LRANGE）
- `:39` 追加消息（RPUSH）
- `:43` 刷新过期（EXPIRE 60）
- `:47` 再读并返回

5. `docker-compose.yml`
- 看 Redis 容器如何被一键启动

---

## 6. 关键术语超短解释

- Spring Boot：帮助你“少配置、快启动”地写 Java Web 服务
- REST API：通过 HTTP 暴露资源接口（这里是 `/api/chat`）
- Redis：内存数据库，读写快，适合缓存和会话数据
- List：有序数组结构，适合按时间保存聊天消息
- TTL：键剩余存活时间；到期自动删除
- RedisTemplate：Spring 提供的 Redis 操作客户端封装

---

## 7. 你可以立刻做的 2 个小练习

1. 把 TTL 从 60 改成 10
- 修改 `ChatController.java` 的 `Duration.ofSeconds(60)`
- 重新运行，观察过期更快发生

2. 把返回结构加上 `ttl`
- 在 Controller 里调用 `redisTemplate.getExpire(key)`
- 把剩余秒数一起返回，前端更容易展示“记忆还剩多久”

