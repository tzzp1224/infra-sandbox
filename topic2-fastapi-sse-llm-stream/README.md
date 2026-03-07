# 课题2：FastAPI + SSE 最小流式接口实验手册（LLM 逐字输出）

这个 Demo 的目标是：用 **Python + FastAPI** 搭一个最小可运行的“类大模型流式推理”接口，模拟逐字返回。

你会得到一个 `POST /generate` 接口：
1. 服务端通过异步生成器逐字产出内容
2. 每隔 0.1 秒发送一个字符
3. 通过 SSE（`text/event-stream`）实时推送给客户端

---

## 1. 你将学到什么

- FastAPI 如何快速构建 HTTP API
- `async def` + `yield` 如何把“生成过程”变成“流”
- SSE（Server-Sent Events）如何实现服务端单向实时推送
- 为什么流式输出适合 LLM 场景（更低等待感知、更省内存峰值）

---

## 2. 项目结构

```text
topic2-fastapi-sse-llm-stream/
├── main.py
└── README.md
```

说明：按你的要求，业务代码只有单文件 `main.py`，可直接运行。

---

## 3. 环境准备

进入目录：

```bash
cd /Users/dexter/Documents/Dexter_Work/infra-sandbox/topic2-fastapi-sse-llm-stream
```

检查 Python：

```bash
python3 -V
```

推荐创建虚拟环境：

```bash
python3 -m venv .venv
source .venv/bin/activate
```

安装依赖：

```bash
pip install fastapi "uvicorn[standard]"
```

### 期待现象

- 能看到 Python 版本（建议 3.10+）
- `pip install` 无报错

### 说明了什么

- FastAPI 提供 Web 框架能力
- Uvicorn 是 ASGI 服务器，负责真正监听端口并处理并发请求

---

## 4. 启动服务

```bash
uvicorn main:app --reload
```

或直接：

```bash
python3 main.py
```

### 期待现象

终端出现类似日志：
- `Uvicorn running on http://127.0.0.1:8000`
- `Application startup complete`

### 说明了什么

- ASGI 服务已启动，接口可访问
- `--reload` 会在你改代码后自动重启，适合实验学习

---

## 5. 实验步骤（命令 + 现象 + 含义 + 代码定位）

## Step 1：查看 API 文档（了解请求结构）

浏览器打开：
- [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)

### 期待现象

- 出现 Swagger UI
- 能看到 `POST /generate`

### 说明了什么

- FastAPI 自动基于类型注解和 Pydantic 模型生成交互文档

### 对应代码

- `main.py:9-10`：`GenerateRequest` 请求模型
- `main.py:21-23`：`POST /generate` 路由定义与返回

---

## Step 2：用 curl 触发流式返回（核心实验）

```bash
curl -N -X POST http://127.0.0.1:8000/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"你好，FastAPI和SSE正在逐字返回。"}'
```

### 期待现象

你会持续看到类似输出（逐条到达，而非一次性返回）：

```text
data: 你

data: 好

data: ，
...

event: done
data: [DONE]
```

### 说明了什么

- `-N` 关闭 curl 缓冲，能看到“边生成边到达”
- 服务端不是等完整文本算完再返回，而是每 0.1 秒推一段
- 这就是 LLM 常见的流式响应体验

### 对应代码

- `main.py:15`：`await asyncio.sleep(0.1)` 模拟推理耗时
- `main.py:16-17`：`yield` 按 SSE 协议逐段输出（含内存/网络意义注释）
- `main.py:23`：`StreamingResponse(..., media_type="text/event-stream")`

---

## Step 3：验证响应头（理解为什么能流式）

```bash
curl -N -i -X POST http://127.0.0.1:8000/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"abc"}'
```

### 期待现象

响应头里可看到：
- `content-type: text/event-stream; charset=utf-8`

### 说明了什么

- 客户端会按事件流解析，而不是当普通 JSON 一次性处理

### 对应代码

- `main.py:23`：`media_type="text/event-stream"`

---

## Step 4：对比“短文本 vs 长文本”体验

短文本：

```bash
curl -N -X POST http://127.0.0.1:8000/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"短句"}'
```

长文本：

```bash
curl -N -X POST http://127.0.0.1:8000/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"这是一个更长的句子，用来观察流式输出时用户感知的等待时间变化。"}'
```

### 期待现象

- 长文本时，你会明显感到“早开始显示，持续追加”

### 说明了什么

- 流式响应显著改善首字等待时间（TTFT）感知
- 用户不必等待完整答案完成才看到结果

---

## 6. 代码快速读懂（5 分钟）

按这个顺序看 `main.py`：

1. `main.py:6` `app = FastAPI()`
- 创建 Web 应用对象

2. `main.py:9-10` `class GenerateRequest(BaseModel)`
- 定义请求体 JSON 结构（`prompt` 字段）

3. `main.py:13-18` `async def llm_stream(text: str)`
- 核心：异步生成器
- `await asyncio.sleep(0.1)` 模拟模型每步推理耗时
- `yield ...` 每次产生一个字符事件

4. `main.py:21-23` `@app.post("/generate")`
- 接收请求后，把异步生成器交给 `StreamingResponse`
- 立即建立流式响应通道，后续边算边发

5. `main.py:26-29` `if __name__ == "__main__": ...`
- 允许直接 `python3 main.py` 启动 Uvicorn

---

## 7. 技术栈在真实业务中的应用与用法

## 7.1 FastAPI 在实际项目中常见用途

- LLM 网关层：统一封装模型调用、鉴权、日志、限流
- 推理编排层：一个请求串联检索、重排、模型生成
- 工具服务层：向前端暴露稳定 HTTP API

实战用法要点：
- 用 Pydantic 做参数校验，减少无效请求
- 用依赖注入管理数据库/缓存客户端
- 用中间件打点耗时、追踪 request-id

## 7.2 SSE 在实际项目中常见用途

- LLM token 流式输出（最典型）
- 长任务进度推送（如文档解析、训练任务进度）
- 实时日志推送（构建日志、执行日志）

实战用法要点：
- SSE 适合“服务端 -> 客户端”单向流
- 如果需要双向实时通信（客户端也频繁主动推消息），考虑 WebSocket
- 生产环境反向代理要关闭缓冲（否则会被攒包后再发，失去流式效果）

## 7.3 为什么 LLM 场景偏爱流式

- 用户更快看到首字，体感延迟更低
- 服务端无需等全文生成完成，内存峰值更平滑
- 前端可边接收边渲染，支持“停止生成”等交互扩展

---

## 8. 小结（你现在应该掌握）

完成本实验后，你已经实践了：
- FastAPI 最小接口搭建
- 异步生成器流式产出
- SSE 协议格式和客户端消费方式
- LLM 场景下流式返回的工程价值

---

## 9. 可选进阶练习（建议下一步）

1. 把“逐字”改成“逐词”
- 将 `for ch in text` 改为按单词切分输出，观察体验差异

2. 在 SSE 中增加事件类型
- 除了 `data`，加 `event: token`、`event: done`，让前端按事件处理

3. 增加断开检测
- 客户端中断连接时，服务端尽快停止生成，节省算力
