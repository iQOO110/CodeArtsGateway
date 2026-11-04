# 待优化清单（TODO）

> 记录当前已知但未完成的优化项。优先级：P0 最高，P3 最低。

| # | 事项 | 现状 | 建议方案 | 优先级 |
|---|------|------|----------|--------|
| 1 | 码道会话绑定持久化 | `devCloudSessions` 仅存在内存（`GatewayServer.java:44`），App 进程重启或被系统杀掉后 `sessionKey -> session_id` 全部丢失，下次请求会新建会话 | 将映射写入 SharedPreferences，启动或首次请求时恢复 | P1 |
| 2 | 旧会话有效性校验 | 没有启动校验或健康检查，只在上游请求失败后由 `markSessionFailed` 触发重建 | 用 `GET /codebaseservice/v1/cloudagent/sessions?page=1&pageSize=10` 校验 sid，失效则新建 | P1 |
| 3 | 旧会话回收 | 网关不主动删除自己创建的旧会话，重启后旧会话继续占用 3 个并发名额 | 记录网关创建的 sid 与时间，超时或超量时清理，只删网关自己创建的会话 | P1 |
| 4 | sessionKey 移除 model 依赖 | 派生键包含 model（`GatewayServer.java:2969`），同一对话切换模型会被当成新对话，上下文断开 | 改用显式会话 ID、首条 user 与 assistant 指纹区分；同一对话切模型保持同一 sid | P2 |
| 5 | 会话过期策略 | 未定义 TTL，无法预判上游会话何时过期 | 记录创建与最后使用时间，超过阈值主动重建 | P2 |
| 6 | 文件附件透传 | `ProtocolAdapter.flattenContent` 只把图片/文件转成 `[图片]`、`[文件: xxx]`，内容不会到达码道 | 先实现文本类附件解析；图片/PDF 需要上游上传接口；不支持时明确报错，避免静默占位 | P2 |
| 7 | 免费额度接口不可用 | `benefit/tokens/balance` 返回 `invalid channel, group not found`；1.9.15 起已把完整响应写入 `codearts-package.json` | 用 balance raw 确认是账号权限问题还是接口参数变更 | P2 |
| 8 | 产物下载 token 语义 | 注释写“一次性”，实际在 200 个 LRU 缓存淘汰前可重复下载 | 明确为短期有效 token，或消费后立即删除 | P3 |
| 9 | 客户端历史裁剪误判 | 1.9.16 用“本轮历史是否包含上轮 assistant 回复”判断新对话，客户端裁剪历史时会误判为新对话 | 增加时间窗口或显式会话 ID 支持，降低误判 | P3 |
| 10 | 多客户端并发 | 同一用户多客户端接入且缺少显式会话 ID 时，派生键加指纹仍有冲突可能 | 优先读取客户端会话 ID；必要时按客户端来源隔离 | P3 |