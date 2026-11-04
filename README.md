# CodeArts 网关（CodeArtsGateway）v1.9.18 手机版源码

CodeArts 网关的完整 Android 源码工程（versionCode 46，versionName 1.9.18），复用同一份网关核心的 Windows 桌面版见 `desktop/`。

## 项目简介

运行在 Android 设备上的 OpenAI 兼容本地网关：对上游伪装成华为 CodeArts 网页端会话，对下游客户端提供 `/v1/chat/completions`、`/v1/models` 等标准 OpenAI 接口；内置 function calling 垫片（ToolShim）、工具上游转发（ToolUpstream）与 MCP 客户端（McpHub），可将任意 OpenAI 兼容客户端接入码道模型。

主要能力：

- OpenAI 兼容 API：`/v1/chat/completions`（流式 SSE / 非流式）、`/v1/models`
- 码道协议适配：会话创建、流式解析、思维链（thinking）事件透出
- function calling 垫片：客户端 `tools` → 协议文本注入 → 哨兵解析 → `tool_calls` 回传
- 工具路径：原生 function calling 上游转发 + MCP 服务器接入（Streamable HTTP）
- 网关侧 API Key 管理（`sk-codearts-*`）、局域网监听开关、开机自启、悬浮球

## 目录结构

```
app/src/main/java/com/codearts/gateway/
├── core/                  # 网关核心
│   ├── GatewayServer.java   # HTTP 服务器与全部路由
│   ├── DevCloudClient.java  # 上游（CodeArts 网页端）客户端
│   ├── ProtocolAdapter.java # 码道协议适配
│   ├── ToolShim.java        # function calling 垫片
│   ├── ToolUpstream.java    # 工具上游转发
│   ├── McpHub.java          # MCP 客户端（Streamable HTTP）
│   ├── Store.java           # 配置存储（SharedPreferences）
│   ├── AppConfig.java       # 应用常量与取令牌脚本
│   ├── Actions.java         # 签到/领包等动作
│   ├── Diagnostics.java     # 一键诊断
│   ├── QuestionReply.java   # 码道提问的选项/文本回填
│   ├── ArtifactStore.java   # 本轮产物落盘与下载缓存
│   └── ...
├── service/               # 前台服务、开机自启、悬浮球
└── ui/                    # 登录、主界面、设置、模型、密钥、诊断页面
```

## 构建要求

- JDK 17
- Android SDK：compileSdk 34，Build-Tools 35.0.0（AGP 8.7.3）
- Gradle 8.14.2（仓库已带 wrapper，执行 `./gradlew` 会自动获取）

## 构建步骤

1. 在项目根目录创建 `local.properties`，写入本机 SDK 路径：

   ```properties
   sdk.dir=/path/to/your/android-sdk
   ```

2. 编译 Debug 包：

   ```bash
   ./gradlew assembleDebug
   ```

   产物：`app/build/outputs/apk/debug/app-debug.apk`

3. 如需 Release 包：`./gradlew assembleRelease`（工程未配置签名，需自行添加 `signingConfig`）。

> 依赖均来自 aliyun/google/mavenCentral 公共仓库（见 `settings.gradle`），无私有仓库。

## ARM64 Linux 构建（如手机容器 / 云主机）

Google 官方 build-tools 内的 `aapt2`、`zipalign` 只有 x86_64 版本，aarch64 主机需要 QEMU 用户态模拟：

1. 安装 QEMU 用户态模拟器与 x86_64 glibc 最小根目录（`qemu-x86_64-static -L <sysroot>`）。
2. 把 `build-tools/35.0.0/aapt2` 与 `zipalign` 备份为 `.orig`，用 shell 包装脚本调用 QEMU。
3. 构建时把 AGP 的 aapt2 指向包装脚本：

   ```bash
   ./gradlew -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.0/aapt2 :app:assembleDebug
   ```

产物同样在 `app/build/outputs/apk/debug/app-debug.apk`。

## 凭据与敏感信息说明

- 源码中**不含任何硬编码凭据**：上游 Cookie（`devclouddevuibjtcftk`）、MCP token、工具上游 Key 均为运行时由用户在 App 界面输入，保存在应用私有存储（SharedPreferences），不随源码分发。
- `DevCloudClient.AGENT_ID` 为上游前端 bundle 中的公共 agent 标识常量，非私密凭据。
- 详细扫描结论见 `SENSITIVE_SCAN.md`。
