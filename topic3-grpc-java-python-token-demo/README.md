# 课题3：跨语言 gRPC 最小可运行 Demo（Java Client -> Python Server）

场景：Java 业务系统向 Python AI 系统发起底层 Token 分析请求。

目标：
- 用一份 `agent_service.proto` 作为跨语言契约
- Python 实现 gRPC 服务端
- Java（非 Spring Boot）用 `main` 方法直接调用

---

## 1. 你将学到什么

- `.proto` 如何成为 Java/Python 共享的接口契约
- Python 用 `grpcio-tools` 生成并实现服务端
- Java 用 Maven 插件从同一份 proto 生成客户端 Stub
- 为什么 Protobuf 比 JSON 更适合高频 RPC

---

## 2. 项目结构

```text
topic3-grpc-java-python-token-demo/
├── proto/
│   └── agent_service.proto
├── python-server/
│   ├── requirements.txt
│   └── server.py
├── java-client/
│   ├── pom.xml
│   └── src/main/java/com/demo/grpc/AgentClientMain.java
└── README.md
```

---

## 3. 环境准备

```bash
cd /Users/dexter/Documents/Dexter_Work/infra-sandbox/topic3-grpc-java-python-token-demo

python3 -V
java -version
mvn -v
```

### 期待现象

- Python（建议 3.10+）
- Java（建议 17）
- Maven 可用

### 说明了什么

- Python 负责服务端
- Java + Maven 负责客户端与 proto 代码生成

---

## 4. 先看“契约”：agent_service.proto

文件：`proto/agent_service.proto`

它定义了：
- 请求：`AnalyzeRequest { string text }`
- 响应：`AnalyzeResponse { int32 length, string status }`
- 服务：`AgentService.AnalyzeText(...)`

这份文件就是跨语言统一协议，Python 和 Java 都从它生成代码，避免手写协议漂移。

---

## 5. 实验步骤（命令 + 预期现象 + 含义 + 代码对应）

## Step 1：安装 Python 依赖

```bash
cd /Users/dexter/Documents/Dexter_Work/infra-sandbox/topic3-grpc-java-python-token-demo/python-server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 期待现象

- `grpcio`、`grpcio-tools` 安装成功

### 说明了什么

- `grpcio` 提供运行时
- `grpcio-tools` 提供 proto -> Python 代码生成器

### 对应代码

- `python-server/requirements.txt`

---

## Step 2：编译 proto（Python 侧，必须执行）

```bash
cd /Users/dexter/Documents/Dexter_Work/infra-sandbox/topic3-grpc-java-python-token-demo/python-server
python -m grpc_tools.protoc \
  -I ../proto \
  --python_out=. \
  --grpc_python_out=. \
  ../proto/agent_service.proto
```

### 期待现象

目录下生成：
- `agent_service_pb2.py`
- `agent_service_pb2_grpc.py`

### 说明了什么

- 同一份 `.proto` 被编译成 Python 可调用的强类型代码

### 对应代码

- `server.py` 导入这两个生成文件后实现业务逻辑

---

## Step 3：启动 Python gRPC 服务端

```bash
cd /Users/dexter/Documents/Dexter_Work/infra-sandbox/topic3-grpc-java-python-token-demo/python-server
source .venv/bin/activate
python server.py
```

### 期待现象

看到输出：

```text
Python gRPC server running on 50051
```

### 说明了什么

- 服务端已监听 `50051`
- 等待 Java 客户端 RPC 调用

### 对应代码

- `server.py`：`add_AgentServiceServicer_to_server(...)`
- `server.py`：`server.add_insecure_port("[::]:50051")`

---

## Step 4：编译 proto（Java 侧，通过 Maven 插件自动执行）

```bash
cd /Users/dexter/Documents/Dexter_Work/infra-sandbox/topic3-grpc-java-python-token-demo/java-client
# 显式触发 proto 生成 + gRPC 代码生成
mvn protobuf:compile protobuf:compile-custom
# 或直接编译（已绑定到生命周期）
mvn compile
```

### 期待现象

- Maven 下载依赖并成功编译
- 生成 Java gRPC 类到 `target/generated-sources`（如 `AgentServiceGrpc`、`AnalyzeRequest`、`AnalyzeResponse`）

### 说明了什么

- Java 并没有手写 DTO 和网络协议解析，而是从同一 proto 自动生成

### 对应配置（Maven 插件）

- `java-client/pom.xml` 中 `protobuf-maven-plugin`
- 关键点：`<protoSourceRoot>${project.basedir}/../proto</protoSourceRoot>`，指向共享契约目录

---

## Step 5：运行 Java 客户端 main 调用 Python 服务

保持 Python 服务端窗口不要关闭，另开终端执行：

```bash
cd /Users/dexter/Documents/Dexter_Work/infra-sandbox/topic3-grpc-java-python-token-demo/java-client
mvn -q exec:java -Dexec.mainClass=com.demo.grpc.AgentClientMain
```

### 期待现象

客户端输出类似：

```text
status=OK, length=11
```

### 说明了什么

- Java 成功远程调用 Python 服务
- `hello token` 的字符长度被 Python 计算并回传

### 对应代码

- `AgentClientMain.java`：`stub.analyzeText(...)` 发起 RPC
- `AgentClientMain.java`：`response.getLength()` 直接读取强类型字段

---

## 6. 重点观察：proto 作为跨语言“契约”

你可以做一个小实验验证契约约束：

1. 在 `proto/agent_service.proto` 给 `AnalyzeResponse` 新增字段（例如 `string engine = 3;`）
2. 重新执行 Python 的 protoc 和 Java 的 `mvn compile`
3. 两端都会获得同名同类型的新字段访问器

这说明：
- 协议变更集中在 `.proto`
- 各语言通过生成代码同步，不靠口头约定或手写 JSON 字段名

---

## 7. gRPC vs JSON：这次实验的体验差异

如果你走 JSON 常见写法，Java 客户端通常要：
- 收到字符串 JSON
- 用 Jackson/Gson 手动反序列化
- 手动处理字段缺失、类型不匹配、拼写错误

而本实验的 gRPC + Protobuf：
- 直接 `AnalyzeResponse response = stub.analyzeText(...)`
- 直接 `response.getLength()`
- 字段类型和方法签名由编译器保障

工程收益：
- 包体更小（二进制编码）
- 序列化/反序列化更快
- 类型安全更强，重构更稳

---

## 8. 代码快速看懂（10 分钟）

建议按这个顺序：

1. `proto/agent_service.proto`
- 先看“契约”定义（消息结构 + RPC 方法）

2. `python-server/server.py`
- 看 `AnalyzeText` 如何把 `request.text` 映射成 `length + status`

3. `java-client/pom.xml`
- 看 `protobuf-maven-plugin` 如何从共享 `../proto` 自动生成 Java 代码

4. `java-client/src/main/java/com/demo/grpc/AgentClientMain.java`
- 看 `ManagedChannel`、`BlockingStub`、`response.getLength()` 的“像本地方法一样”调用体验

---

## 9. 技术栈在实际应用中的用法

## 9.1 gRPC/Protobuf 常见场景

- 微服务内部高频调用（低延迟、强类型）
- 跨语言系统互调（Java 业务层 <-> Python AI 层）
- 流式任务（语音、Token 流、实时特征传输）

## 9.2 Java + Python 的典型分工

- Java：鉴权、交易流程、审计、核心业务编排
- Python：模型推理、NLP、数据科学组件
- gRPC：连接两者的高效 RPC 总线

## 9.3 生产落地建议

- 内网优先 TLS（`useTransportSecurity`）替代明文
- 给 RPC 加超时与重试策略
- 在 proto 做版本管理（新增字段用新 tag，避免复用旧 tag）

---

## 10. 常见故障排查

1. Java 调用报连接失败
- 检查 Python 服务是否已启动并监听 `50051`

2. Python 启动报找不到 `agent_service_pb2`
- 说明还没执行 `grpc_tools.protoc` 生成 Python 文件

3. Java 编译报找不到 `AnalyzeResponse`
- 说明 Maven proto 生成未执行成功，先跑 `mvn compile`

---

## 11. 一句话总结

这次实验的核心不是“写网络请求”，而是：
- 用 `.proto` 先定义契约
- 两端各自生成强类型代码
- 把跨语言调用变成“面向对象的方法调用”体验
