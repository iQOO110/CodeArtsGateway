# InkStone 移植遗留标识清单（v1.9.9 源码树）

本工程由 Intern InkStone 网关移植而来。经全源码检索，以下 `inkstone` /
`intern-inkstone` 字样均为**惰性遗留**：不在 CodeArts 实际链路上触发，不影响运行
行为与身份自报（`AppConfig.APP_NAME` 已在 v1.9.4 修正为「CodeArts 网关」）。
交付时**未修改代码**，清单如下，供使用者自行决定是否清理。

| 位置 | 内容 | 性质 |
|---|---|---|
| `build.gradle:1` | 注释「InkStone 网关 —— 顶层构建脚本」 | 纯注释 |
| `ui/ModelsActivity.java:30` | 注释「Intern InkStone 上游」 | 纯注释 |
| `ui/ModelsActivity.java:206` | 剪贴板标签 `newPlainText("inkstone", …)` | 惰性：仅系统剪贴板内部标签，不外发 |
| `core/ProtocolAdapter.java:9` | 类注释「Intern InkStone orbit 自定义 SSE」 | 纯注释 |
| `core/ProtocolAdapter.java:305,329` | 流式分片 model 兜底 `"intern-inkstone"` | 惰性：仅当调用方传空 model 时使用；当前 CodeArts 链路恒传非空 model，不触发 |
| `core/ProtocolAdapter.java:454` | `modelsToOpenAi(String)` 中 `owned_by: "intern-inkstone"` | **死代码**：该方法在源码中无任何调用点；活跃路径是 `Store.modelsToOpenAi`（`owned_by: "codearts-devcloud"`，与实机行为一致） |
| `core/GatewayServer.java:35` | 类注释「对内 Intern InkStone orbit：OrbitClient」 | 纯注释；引用的 OrbitClient 类不存在于本树 |
| `core/AppConfig.java:19` | `DEFAULT_ORIGIN = https://discovery.intern-ai.org.cn` | 惰性：源码中无引用；仅 ORIGIN/ORBIT_BASE/LOGIN_URL 派生自它，其中 ORBIT_BASE 仅用于 /health 的 upstream 自报与旧协议路径（未接线） |
| `core/AppConfig.java:22` | 注释「Intern InkStone 官方站点」 | 纯注释 |

## 顺带说明（非遗留，但核对时易混淆）

- `AppConfig.VERSION = "1.9.4"` 与 `app/build.gradle` 的 `versionName "1.9.9"`
  （versionCode 39）不一致：常量在 v1.9.4 修正时未随版本联动，/health 自报版本
  以常量为准。如需一致，把常量改为 `"1.9.9"` 即可。
- 若决定清理：可安全删除/替换上表中全部注释与字符串；`ProtocolAdapter.modelsToOpenAi`
  为死代码可整体删除；`AppConfig` 的 DEFAULT_ORIGIN/ORBIT_BASE/LOGIN_URL 三常量
  删除前需同步移除 `ProtocolAdapter.java:182/187/191` 与 `GatewayServer.java:1683`
  的引用点。
