# 华为云码道 (CodeArts) AI 提问与多轮交互完整参数与通信流程实测记录

> **测试时间**：2026-11-04  
> **测试环境**：Ego Lite / Chromium (Windows x64)  
> **测试模型**：`DeepSeek-V4.1-Flash-1017`  
> **测试目标**：验证码道原生提问卡片弹出、等待挂起、交互选项提交与原 SSE 流续答恢复的完整链路。

---

## 目录
1. [核心链路与通信时序模型](#一核心链路与通信时序模型)
2. [Step 1：新建任务与会话创建（POST /sessions）](#二step-1新建任务与会话创建)
3. [Step 2：发送任务并监听 SSE 流（POST /messages）](#三step-2发送任务并监听-sse-流)
4. [Step 3：捕获提问事件与原始数据包结构（event: question）](#四step-3捕获提问事件与原始数据包结构)
5. [Step 4：交互提交作答（POST /messages/reply）](#五step-4交互提交作答)
6. [Step 5：原流续答恢复与任务收尾](#六step-5原流续答恢复与任务收尾)
7. [Step 6：产物下载链路实测验证（GET /files/download）](#七step-6产物下载链路实测验证)
8. [Step 7：一次性整包拉取全部产物（recursive=true）](#八step-7一次性整包拉取全部产物)
9. [关键 Header 与避坑要点汇总](#九关键-header-与避坑要点汇总)

---

## 一、核心链路与通信时序模型

```
[客户端 / Web 前端]                       [华为云服务端 (CodeArts DevCloud)]
       |                                                |
       |--- 1. POST /chat/v1/cloudagent/sessions ------>| (创建会话，获取 sessionId)
       |<== 返回 { result: { session_id } } <===========|
       |                                                |
       |--- 2. POST /chat/codebaseservice/.../messages ->| (发送指令，建立 SSE 长连接)
       |<== HTTP 200 text/event-stream =================|
       |<-- event: status (initializing/processing) ----|
       |<-- event: thought (模型深度思考增量) ----------|
       |<-- event: file_diff (touch /workspace/a) ------|
       |<-- event: question (下发交互提问卡片) ---------|
       |    [关键机制：服务端挂起等待回复，连接不中断] |
       |                                                |
       |--- 3. POST /chat/v1/cloudagent/.../reply ----->| (提交所选答案文本)
       |<== 返回 HTTP 200 { answered: true } <==========| (解除挂起状态)
       |                                                |
       |    [原 SSE 连接恢复推流]                       |
       |<-- event: thought (续答思考) ------------------|
       |<-- event: message (回答正文) ------------------|
       |<-- event: done (任务完成，产物摘要) -----------|
```

---

## 二、Step 1：新建任务与会话创建

### 1. HTTP 请求报文
```http
POST /chat/v1/cloudagent/sessions HTTP/1.1
Host: devcloud.cn-north-4.huaweicloud.com
Origin: https://devcloud.cn-north-4.huaweicloud.com
Referer: https://devcloud.cn-north-4.huaweicloud.com/chat/home
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
Cookie: devclouddevuibjtcftk=G4OL-RDUO-4Q6R-M8GR-AHJG-4TOP-V3KN-09FI; devclouddevuibjJ_SESSION_ID=...; SessionID=...; [其余完整 Cookie]
cftk: G4OL-RDUO-4Q6R-M8GR-AHJG-4TOP-V3KN-09FI
Agent-Type: CodeBase
x-codearts-doer-scenario-type: web
x-requested-with: XMLHttpRequest
Content-Type: application/json

{}
```

### 2. HTTP 响应报文
```http
HTTP/1.1 200 OK
Content-Type: application/json;charset=UTF-8

{
  "status": "ok",
  "result": {
    "session_id": "49065592-5bcb-4763-8c54-9d3a1393e330",
    "runtime_name": "runtime-codeartsdoer-v3",
    "status": "ACTIVE",
    "streaming": false,
    "last_active_time": "2026-11-04 13:40:02",
    "create_time": "2026-11-04 13:40:02",
    "update_time": "2026-11-04 13:40:02",
    "client_type": "web"
  }
}
```

---

## 三、Step 2：发送任务并监听 SSE 流

### 1. HTTP 请求报文
```http
POST /chat/codebaseservice/v1/cloudagent/sessions/49065592-5bcb-4763-8c54-9d3a1393e330/messages HTTP/1.1
Host: devcloud.cn-north-4.huaweicloud.com
Origin: https://devcloud.cn-north-4.huaweicloud.com
Referer: https://devcloud.cn-north-4.huaweicloud.com/chat/home?sessionId=49065592-5bcb-4763-8c54-9d3a1393e330
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
Cookie: devclouddevuibjtcftk=G4OL-RDUO-4Q6R-M8GR-AHJG-4TOP-V3KN-09FI; [完整 Cookie]
cftk: G4OL-RDUO-4Q6R-M8GR-AHJG-4TOP-V3KN-09FI
Agent-Type: CodeBase
x-codearts-doer-scenario-type: web
x-requested-with: XMLHttpRequest
Accept: text/event-stream
Content-Type: application/json

{
  "content": "请在 /workspace 下创建一个空文本 a，完成后使用提问功能向我询问下一步计划。",
  "repos": [],
  "model_id": "deepseek-v4.1-flash-1017"
}
```

### 2. 执行过程中的推流报文（片段）
```http
event: status
data: {"status":"initializing","turn_id":"turn_001","ts":1793770802000}

event: thought
data: {"content":"用户需要我们在 /workspace 下创建空文本 a 并主动提问下一步。执行 touch 并使用 question 工具...","index":1,"ts":1793770803100}

event: file_diff
data: {"diff":[{"path":"/workspace/a","type":"create","content":""}],"ts":1793770804000}
```

---

## 四、Step 3：捕获提问事件与原始数据包结构

模型执行创建操作后，下发 `event: question`，并在前端渲染出交互选项卡片，会话进入等待状态。

### 1. SSE 推流原始报文
```http
event: question
data: {
  "requestId": "que_1a505b223001v0p3u88x7e29a0",
  "callId": "call_62b18aa2f90141f98bc19d44",
  "questions": [
    {
      "header": "下一步计划",
      "question": "文件 a 已创建完成，请问下一步您想做什么？",
      "multiple": false,
      "options": [
        {
          "label": "向 a 写入内容",
          "description": "我会把您指定的文本写入文件 a"
        },
        {
          "label": "查看 a 的状态",
          "description": "确认文件是否存在、大小与权限等属性"
        },
        {
          "label": "重命名或删除 a",
          "description": "按您的要求调整该文件"
        },
        {
          "label": "暂时不用，结束本次操作",
          "description": "保持文件不变，结束当前任务"
        }
      ]
    }
  ]
}
```

### 2. 字段规范说明
- **`requestId`**：提问唯一 ID（固定 `que_` 开头）。在提交 Reply 时必须映射为 **`question_id`**。
- **`callId`**：工具调用 ID（固定 `call_` 开头），对应前端标准 tool call。
- **`options[].label` 与 `options[].description`**：前端交互渲染的选项名和说明描述。

---

## 五、Step 4：交互提交作答

用户在页面勾选（或由调用方发送）选项后，向服务端发起作答请求以**解除等待**。

### 1. HTTP 请求报文
```http
POST /chat/v1/cloudagent/sessions/49065592-5bcb-4763-8c54-9d3a1393e330/messages/reply HTTP/1.1
Host: devcloud.cn-north-4.huaweicloud.com
Origin: https://devcloud.cn-north-4.huaweicloud.com
Referer: https://devcloud.cn-north-4.huaweicloud.com/chat/home?sessionId=49065592-5bcb-4763-8c54-9d3a1393e330
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
Cookie: devclouddevuibjtcftk=G4OL-RDUO-4Q6R-M8GR-AHJG-4TOP-V3KN-09FI; [完整 Cookie]
cftk: G4OL-RDUO-4Q6R-M8GR-AHJG-4TOP-V3KN-09FI
Agent-Type: CodeBase
x-codearts-doer-scenario-type: web
x-ot-client-type: CodeArtsWork_Web
x-ot-client-version: 126.10.7
x-requested-with: XMLHttpRequest
Content-Type: application/json

{
  "question_id": "que_1a505b223001v0p3u88x7e29a0",
  "answers": [
    [
      "\"向 a 写入内容\" - 我会把您指定的文本写入文件 a"
    ]
  ]
}
```

> **注意**：`answers` 的元素格式必须严格符合前端规范：`"<label>" - <description>`（带外层引号与横杠分隔）。

### 2. HTTP 响应报文
```http
HTTP/1.1 200 OK
Content-Type: application/json;charset=UTF-8

{
  "status": "ok",
  "result": {
    "questionId": "que_1a505b223001v0p3u88x7e29a0",
    "answered": true
  }
}
```

---

## 六、Step 5：原流续答恢复与任务收尾

提交 Reply 成功后，原本挂起的 **Step 2 原 SSE 连接立即恢复吐字**，无需重新发起新的 `/messages` 请求。

### 1. 原 SSE 连接恢复推流的报文
```http
event: thought
data: {"content":"收到用户答复：向 a 写入内容。准备向用户询问具体写入文本并完成任务...","index":2,"ts":1793770830100}

event: message
data: {"content":"您选择了「向 a 写入内容」。请告诉我需要写入文件 a 的具体文本内容，收到后我会立即写入并确认结果。","index":1,"ts":1793770831000}

event: done
data: {
  "status": "completed",
  "turn_id": "49065592-5bcb-4763-8c54-9d3a1393e330-1",
  "artifact_summary": {
    "total": 1,
    "artifacts": [
      {
        "name": "a",
        "path": "/workspace/a",
        "type": "file",
        "size": 0
      }
    ]
  }
}
```

---

## 七、Step 6：产物下载链路实测验证

在任务完成（`event: done`）且服务端返回 `artifact_summary` 之后，直接通过文件下载网络接口拉取码道工作区生成的产物文件。

### 1. HTTP 下载请求报文
```http
GET /chat/codebaseservice/v1/cloudagent/sessions/bfce6921-29f1-45d3-b5d9-2a630989b811/files/download?path=hello.txt&recursive=false HTTP/1.1
Host: devcloud.cn-north-4.huaweicloud.com
Origin: https://devcloud.cn-north-4.huaweicloud.com
Referer: https://devcloud.cn-north-4.huaweicloud.com/chat/home?sessionId=bfce6921-29f1-45d3-b5d9-2a630989b811
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
Cookie: devclouddevuibjtcftk=G4OL-RDUO-4Q6R-M8GR-AHJG-4TOP-V3KN-09FI; [完整 Cookie]
cftk: G4OL-RDUO-4Q6R-M8GR-AHJG-4TOP-V3KN-09FI
x-codearts-doer-scenario-type: web
x-requested-with: XMLHttpRequest
Accept: application/octet-stream, application/json, */*
```

### 2. HTTP 响应报文
```http
HTTP/1.1 200 OK
Content-Type: application/octet-stream
Content-Length: 47

Hello Huawei CodeArts! Generated at 2026-11-04.
```

### 3. 下载路径兼容性实测结论（A15 根因验证）
测试传入不同格式的 `path` 参数实测响应：
- `path=hello.txt`（相对路径）：**HTTP 200**，成功返回 47 字节二进制流；
- `path=%2Fworkspace%2Fa`（绝对路径编码）：**HTTP 200**，成功匹配工作区；
- `path=/workspace/a`（未编码绝对路径）：**HTTP 200**，成功匹配工作区；
- `path=workspace/a`（缺少前导斜杠的伪绝对路径）：**HTTP 404** `path 'workspace/a' does not exist`。

---

## 八、Step 7：一次性整包拉取全部产物

码道后端支持通过将路径指定为 `%2Fworkspace`（即 `/workspace`）并带上 `recursive=true` 参数，直接将整个工作区的所有产物文件**一次性打包为标准 `.tar` 归档流**返回。

### 1. HTTP 请求报文
```http
GET /chat/codebaseservice/v1/cloudagent/sessions/bfce6921-29f1-45d3-b5d9-2a630989b811/files/download?path=%2Fworkspace&recursive=true HTTP/1.1
Host: devcloud.cn-north-4.huaweicloud.com
Origin: https://devcloud.cn-north-4.huaweicloud.com
Referer: https://devcloud.cn-north-4.huaweicloud.com/chat/home?sessionId=bfce6921-29f1-45d3-b5d9-2a630989b811
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
Cookie: devclouddevuibjtcftk=G4OL-RDUO-4Q6R-M8GR-AHJG-4TOP-V3KN-09FI; [完整 Cookie]
cftk: G4OL-RDUO-4Q6R-M8GR-AHJG-4TOP-V3KN-09FI
x-codearts-doer-scenario-type: web
x-requested-with: XMLHttpRequest
Accept: application/octet-stream, application/json, */*
```

### 2. HTTP 响应报文
```http
HTTP/1.1 200 OK
Content-Type: application/x-tar
Content-Length: 10240

[二进制 tar 归档数据流]
```

### 3. 解包产物目录结构核实
经 `tar -tf` 实测，内部包含完整的会话产物文件以及工作区配置：
```text
workspace/
workspace/hello.txt
workspace/.codeartsdoer/
workspace/.codeartsdoer/mem-exp-config.json
workspace/.codeartsdoer/mcp/
workspace/.codeartsdoer/mcp/mcp_settings.json
```

---

## 九、关键 Header 与避坑要点汇总

1. **CSRF 双重校验（`cftk`）**：
   - 必须提取 Cookie 中的 `devclouddevuibjtcftk` 值，并在每一个请求中通过 HTTP 请求头 `cftk: <value>` 显式附带，否则直接返回 `400 {"error": "csrf validate fail."}`。

2. **客户端身份路由（`Agent-Type`）**：
   - 会话与对话接口必须指定 `Agent-Type: CodeBase`；
   - 缺失或空值会报 `400 {"error_code":"TM.00001001","error_msg":"请求头Agent-Type为空"}`；非法值会报 `Agent-Type非法`。

3. **并发会话限制（Concurrent Sessions）**：
   - 码道对同一账号限制最大活跃任务数为 **3 个**。若未正常结束或堆积过多空任务，会报 `500 并发会话数已达上限(3个)`。因此每次开始前建议归档或删除旧会话。

4. **SSE 原流生命周期**：
   - 遇到提问时严禁断开原 SSE 连接。Reply 仅返回 `{ answered: true }`，真正的模型回答必须在原长连接流中继续接收。
