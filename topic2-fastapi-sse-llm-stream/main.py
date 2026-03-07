import asyncio
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

app = FastAPI()


class GenerateRequest(BaseModel):
    prompt: str = "这是一个模拟大模型流式推理的示例。"


async def llm_stream(text: str):
    for ch in text:
        await asyncio.sleep(0.1)
        # yield 每次只把当前一个字符留在内存并立刻发送给客户端，避免整段文本一次性驻留并让网络边生成边传输。
        yield f"data: {ch}\\n\\n"
    yield "event: done\\ndata: [DONE]\\n\\n"


@app.post("/generate")
async def generate(req: GenerateRequest):
    return StreamingResponse(llm_stream(req.prompt), media_type="text/event-stream")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
