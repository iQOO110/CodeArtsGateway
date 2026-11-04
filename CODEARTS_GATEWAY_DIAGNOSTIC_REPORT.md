# 码道 CodeArts Web 端网关协议与抓包诊断整合文档

本文档整合了在真实浏览器环境（Ego Lite / Chromium）中抓取到的华为云码道（CodeArts）Web 端完整通信链路、原生抓包报文及状态模型，专门用于指导网关层修复 **A14（互动提问卡住）** 与 **A15（产物下载路径匹配）**。

---

## 目录
1. [诊断核心结论与排查依据](#一诊断核心结论与排查依据)
2. [问题 2：提问 Question 事件、字段名与原始数据结构](#二问题-2提问-question-事件字段名与原始数据结构)
3. [问题 3：空文本产物路径与下载请求抓包原文](#三问题-3空文本产物路径与下载请求抓包原文)
4. [问题 4：互动 Reply 抓包原文与原流续答机制](#四问题-4互动-reply-抓包原文与原流续答机制)
5. [网关改造与落地实现规范](#五网关改造与落地实现规范)

---

## 一、诊断核心结论与排查依据

1. **A14 互动提问卡住问题根因**：
   - **机制原因**：`POST /messages/reply` 接口本身仅为 **ACK 确认提交接口**（立即返回 `{"answered": true}`），**并不在该请求的响应体中返回后续文本**。提交选项后，会话状态解除等待，原本挂起的 **SSE 会话长连接（`/messages` 流）才会继续下发后续的 `thought`、`text`、`done` 事件**。若网关层认为 reply 是类似 Chat 的单次请求响应式接口，或者在提交后掐断/未持续透传原 SSE 流，就会导致界面永远卡死在“等待回复”状态。
   - **字段映射原因**：在 Web 状态树与工具对象中，唯一问答标识字段名为 **`requestId`**（形如 `que_1a1625547001J3tsXA4gR4jABN`）；在提交 `/messages/reply` 时，请求体顶层必须命名为 **`question_id`**。
2. **A15 产物下载路径匹配根因**：
   - 工作空间绝对根目录为 `/workspace`。但前端下载请求 `GET .../files/download?path={path}&recursive=false` 传入的 `path` 参数为**相对工作区的相对路径**（例如根目录下创建 `a`，则 `path=a`；根目录下创建 `1.txt`，则 `path=1.txt`；子目录则为 `path=repo%2Fapp%2Fbuild.gradle`）。
   - 网关层若强行要求带 `/workspace/` 前缀匹配，就会直接触发拒绝或 404。

---

## 二、问题 2：提问 Question 事件、字段名与原始数据结构

### 1. 字段命名规范确认
* **对象内 ID 字段**：**`requestId`**（固定前缀 `que_`，全局唯一）。
* **接口入参 ID 字段**：**`question_id`**。
* **工具名**：`tool = "question"`。
* **Call ID**：`callID = "call_75e7daae370b4e9ab9adf61b"`。

### 2. 前端内部解析后的完整 Question Tool 原始 JSON 数据
以下为会话 `94a7ef5d-21ef-4605-8777-595660b53ad1` 中真实捕获的完整零件数据：

```json
{
  "id": "prt_1a162533a001xD1i3Y4cRJDD9c",
  "type": "tool",
  "tool": "question",
  "callID": "call_75e7daae370b4e9ab9adf61b",
  "title": "question",
  "state": {
    "input": {
      "questions": [
        {
          "question": "空文件 /workspace/a 已创建。接下来你希望我做什么？",
          "header": "下一步",
          "multiple": false,
          "options": [
            {
              "label": "往 a 里写内容",
              "description": "告诉我需要写入的文本，我直接填充"
            },
            {
              "label": "重命名或加扩展名",
              "description": "比如改成 a.txt、a.md 等"
            },
            {
              "label": "删除这个文件",
              "description": "撤销刚才的创建"
            },
            {
              "label": "创建其他文件或项目",
              "description": "说明需求，我来搭建"
            }
          ]
        }
      ]
    },
    "status": "completed",
    "metadata": {
      "filediff": {
        "before": "",
        "after": "",
        "file": "/workspace/a"
      }
    },
    "output": "User has answered your questions: \"空文件 /workspace/a 已创建。接下来你希望我做什么？\"=\"\"往 a 里写内容\" - 告诉我需要写入的文本，我直接填充\". You can now continue with the user's answers in mind.",
    "error": null
  },
  "sessionID": "94a7ef5d-21ef-4605-8777-595660b53ad1",
  "messageID": "msg_1793708934318_94a7ef5d-21ef-4605-8777-595660b53ad1",
  "sessionId": "94a7ef5d-21ef-4605-8777-595660b53ad1",
  "messageId": "msg_1793708934318_94a7ef5d-21ef-4605-8777-595660b53ad1",
  "requestId": "que_1a1625547001J3tsXA4gR4jABN"
}
```

### 3. SSE 推流数据块（Wire 协议格式）
服务端在下发提问时，推流中的事件体格式如下：
```http
event: question
data: {"requestId":"que_1a1625547001J3tsXA4gR4jABN","callId":"call_75e7daae370b4e9ab9adf61b","questions":[{"header":"下一步","question":"空文件 /workspace/a 已创建。接下来你希望我做什么？","multiple":false,"options":[{"label":"往 a 里写内容","description":"告诉我需要写入的文本，我直接填充"},{"label":"重命名或加扩展名","description":"比如改成 a.txt、a.md 等"},{"label":"删除这个文件","description":"撤销刚才的创建"},{"label":"创建其他文件或项目","description":"说明需求，我来搭建"}]}]}
```

---

## 三、问题 3：空文本产物路径与下载请求抓包原文

### 1. 产物生成工具调用原报文
在执行“写一个空文本 a”时，模型输出并由执行器调用的参数原貌：
```json
{
  "type": "tool",
  "tool": "write",
  "state": {
    "input": {
      "filePath": "/workspace/a",
      "content": ""
    },
    "status": "completed",
    "metadata": {
      "diagnostics": {},
      "filepath": "/workspace/a",
      "exists": false,
      "truncated": false,
      "diff": "Index: /workspace/a\n============\n"
    }
  }
}
```
*同理，写空文本 1 时，输入路径为 `/workspace/1.txt`。*

### 2. 产物下载抓包原文（来自 download2.devtools 完整提取）

#### HTTP 请求报文
```http
GET /chat/codebaseservice/v1/cloudagent/sessions/55168a6c-08e7-4182-ab31-ef6806cec871/files/download?path=repo%2Fcodearts-gateway-1.9.10-apple.apk&recursive=false HTTP/1.1
Host: devcloud.cn-north-4.huaweicloud.com
Connection: keep-alive
sec-ch-ua: "Not:A-Brand";v="8", "Chromium";v="156", "Microsoft Edge";v="156"
Accept: application/json, text/plain, */*
cftk: ASCY-KQ6P-ND36-VH9C-F2LB-7CNT-8WE0-QY6V
x-codearts-doer-scenario-type: web
x-requested-with: XMLHttpRequest
sec-ch-ua-platform: "Windows"
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0
Referer: https://devcloud.cn-north-4.huaweicloud.com/chat/home?sessionId=55168a6c-08e7-4182-ab31-ef6806cec871
Accept-Encoding: gzip, deflate, br, zstd
Accept-Language: zh-CN,zh;q=0.9
```

#### HTTP 响应报文
```http
HTTP/1.1 200 OK
Content-Type: application/octet-stream;charset=UTF-8
Content-Disposition: attachment; filename="codearts-gateway-1.9.10-apple.apk"

<Binary Stream>
```

#### 规则说明
- **下载根目录映射**：服务端的基准根目录就是 `/workspace`。
- **对于根目录下的空文件**（例如 `a` 或 `1.txt`）：
  - 传参为：`?path=a&recursive=false` 或 `?path=1.txt&recursive=false`
  - 若客户端传了 `?path=%2Fworkspace%2F1.txt`，网关应剥离 `/workspace/` 前缀后转发。

---

## 四、问题 4：互动 Reply 抓包原文与原流续答机制

### 1. 真实 Reply 提交抓包原文（来自 download4.devtools 完整提取）

#### HTTP 请求报文
```http
POST /chat/v1/cloudagent/sessions/dcd67ec9-b00d-459d-9ba9-b8bc9ece8f34/messages/reply HTTP/1.1
Host: devcloud.cn-north-4.huaweicloud.com
Connection: keep-alive
Content-Length: 192
sec-ch-ua: "Not:A-Brand";v="8", "Chromium";v="156", "Microsoft Edge";v="156"
agent-type: CodeBase
cftk: ASCY-KQ6P-ND36-VH9C-F2LB-7CNT-8WE0-QY6V
x-codearts-doer-scenario-type: web
x-ot-client-type: CodeArtsWork_Web
x-ot-client-version: 126.10.7
x-ot-trace-id: dd50f2c4346c33f37b172d73e62ee3f7
x-ot-span-id: de9229385b26e872
x-snap-traceid: 6e6cac98-211b-4c15-a0bf-bf8e9051
Content-Type: application/json
Accept: application/json, text/plain, */*
x-requested-with: XMLHttpRequest
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0
Origin: https://devcloud.cn-north-4.huaweicloud.com
Referer: https://devcloud.cn-north-4.huaweicloud.com/chat/home?sessionId=dcd67ec9-b00d-459d-9ba9-b8bc9ece8f34
Accept-Encoding: gzip, deflate, br, zstd
Accept-Language: zh-CN,zh;q=0.9

{
  "question_id": "que_1a100d361001zkExFECwrN6F4W",
  "answers": [
    [
      "\"回答一个问题\" - 你提出一个技术或知识问题，我来解答（请在自定义输入框里写下具体问题）"
    ]
  ]
}
```

#### HTTP 响应报文
```http
HTTP/1.1 200 OK
Content-Type: application/json;charset=UTF-8

{
  "status": "ok",
  "result": {
    "questionId": "que_1a100d361001zkExFECwrN6F4W",
    "answered": true
  }
}
```

### 2. 真实提问会话中第二例 Reply 抓包原文（来自 live-network.log）
```http
POST /chat/v1/cloudagent/sessions/94a7ef5d-21ef-4605-8777-595660b53ad1/messages/reply HTTP/1.1
Content-Type: application/json

{
  "question_id": "que_1a1625547001J3tsXA4gR4jABN",
  "answers": [
    [
      "\"往 a 里写内容\" - 告诉我需要写入的文本，我直接填充"
    ]
  ]
}

HTTP/1.1 200 OK
{"status":"ok","result":{"questionId":"que_1a1625547001J3tsXA4gR4jABN","answered":true}}
```

### 3. 原流续答通信模型（关键机制）
```
[Client / Web]                       [Gateway]                      [Huawei Cloud CodeArts]
      |                                  |                                     |
      |--- 1. 发起消息 POST /messages --->|----------- 转发 POST /messages ---->|
      |<== 2. 建立 SSE 长连接流 (200) <===|<======== 建立 SSE 长连接流 ========|
      |<-- 3. event: question ----------|<---------- event: question ---------|
      |   (前端弹出交互选项卡片，           |                                     |
      |    此时 SSE 连接保持挂起等待)      |                                     |
      |                                  |                                     |
      |--- 4. 用户点击选项 POST /reply -->|----------- 转发 POST /reply ------->|
      |<== 5. 返回 {answered: true} <====|<========= 返回 {answered: true} <===|
      |                                  |                                     |
      |                                  |   [关键点：原 SSE 管道未断开，继续推流]  |
      |<-- 6. event: thought (原流) -----|<---------- event: thought ----------|
      |<-- 7. event: text (原流) --------|<---------- event: text -------------|
      |<-- 8. event: done (原流) --------|<---------- event: done -------------|
```

---

## 五、网关改造与落地实现规范

### 1. A14 修复逻辑（`QuestionReply.java` / `GatewayServer.java`）
- **入参解析兼容**：
  从前端请求体中提取 ID 时，必须支持兼容提取：
  ```java
  String questionId = json.optString("question_id", json.optString("requestId", json.optString("id", null)));
  ```
- **转发 Reply 请求体**：
  向华为云发起的请求体必须是：
  ```json
  {
    "question_id": "<questionId>",
    "answers": [ [ "<选中的完整字符串文本>" ] ]
  }
  ```
- **生命周期维持**：
  网关不能将 Reply 处理为一个独立的问答请求；处理 Reply 时**严禁关闭或重置原会话的 SSE 转发线程**，必须确保原流的读取循环持续运行，直到接收到底层的 `event: done` 或连接断开。

### 2. A15 修复逻辑（`ArtifactStore.java` / `ProtocolAdapter.java`）
- **路径规范化**：
  ```java
  public String sanitizeDownloadPath(String path) {
      if (path == null) return "";
      path = path.trim();
      if (path.startsWith("/workspace/")) {
          return path.substring("/workspace/".length());
      }
      if (path.startsWith("/")) {
          return path.substring(1);
      }
      return path;
  }
  ```
- **请求转发**：
  拼接最终 URL 时使用 `?path=" + URLEncoder.encode(sanitizeDownloadPath(rawPath), "UTF-8") + "&recursive=false"`。


---

## 六、实机点击产物下载（Real Download Trigger）抓包实录补充

在当前会话（`94a7ef5d-21ef-4605-8777-595660b53ad1`）中，通过点击右侧产物面板中生成的空文本文件 `a` 对应的下载图标（`.tree-download-btn`），直接由浏览器发出的原生请求抓包：

### 1. 浏览器点击下载图标发起的原生 HTTP 请求
```http
GET /chat/codebaseservice/v1/cloudagent/sessions/94a7ef5d-21ef-4605-8777-595660b53ad1/files/download?path=%2Fworkspace%2Fa&recursive=false HTTP/1.1
Host: devcloud.cn-north-4.huaweicloud.com
Connection: keep-alive
sec-ch-ua: "Not:A-Brand";v="8", "Chromium";v="156", "Google Chrome";v="156"
Accept: application/json, text/plain, */*
cftk: SHHC-2EIR-DGJT-6XWI-TPET-8SGZ-BGYF-ZNI2
language: zh-cn
x-language: zh-cn
x-codearts-doer-scenario-type: web
x-requested-with: XMLHttpRequest
sec-ch-ua-platform: "Windows"
sec-ch-ua-mobile: ?0
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36
Referer: https://devcloud.cn-north-4.huaweicloud.com/chat/home?sessionId=94a7ef5d-21ef-4605-8777-595660b53ad1
Content-Type: application/json
```

### 2. 关键发现：前端组件传入的 path 参数形式
1. **产物面板点击产物文件时的行为**：
   - 前端 `artifact-tree` 组件在点击文件节点右侧的“下载”按钮时，传入的 query 参数为：
     `?path=%2Fworkspace%2Fa&recursive=false`
     **即：前端产物树点击下载时，实际携带的是绝对路径编码（`%2Fworkspace%2Fa`）！**
2. **而历史 download2 抓包中的下载行为**：
   - 之前下载已有 APK 时传入的是：
     `?path=repo%2Fcodearts-gateway-1.9.10-apple.apk&recursive=false`
     **即：相对路径编码！**

### 3. A15 根治结论（对网关实现至关重要）
前端根据不同的调用来源（产物树 vs 仓库树），发出的 `path` 参数**既可能带 `/workspace/`，也可能不带 `/workspace/`**！

网关层的 `files/download` 接口必须实现**双向兼容剥离 / 补全逻辑**：
```java
// 如果目标存储系统要求以 /workspace 为基准的相对路径：
if (path.startsWith("/workspace/")) {
    path = path.substring("/workspace/".length());
} else if (path.startsWith("/")) {
    path = path.substring(1);
}

// 如果目标后端（如 CodeArts Docker 容器 exec/cat）要求绝对路径：
if (!path.startsWith("/workspace/")) {
    path = "/workspace/" + path;
}
```
只要网关在转发时做了这一层归一化，无论前端点击传的是 `path=%2Fworkspace%2F1.txt` 还是 `path=1.txt`，都不会再发生 404/409 或路径不匹配的问题。
