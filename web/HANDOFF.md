# CodeArts 网关 · 双端前端交接说明

面向下一个适配者（人或 AI）的工程说明。读完这份文档即可在不破坏现有结构的前提下，把静态原型接到真实网关。

## 1. 交付内容

- **Windows 端**：`desktop/index.html`
- **手机端**：`mobile/index.html`
- **统一入口**：根目录 `index.html`（选择端）
- **共享源码**：`src/`（core / service / ui / res）
- **双端布局**：`src/desktop/layout.css`、`src/mobile/layout.css`
- **演示数据与模拟服务**：`src/core/data.js`、`src/service/gateway.js`

两端共用同一套页面模板、状态与交互，只有外壳 HTML 和布局 CSS 不同。

## 2. 运行方式

无需构建、无依赖、无网络请求：

```text
直接双击 index.html
或
npx serve .        # 任意静态服务器
```

`file://` 下已验证可用；复制功能在非安全上下文会自动降级到 `execCommand`。

## 3. 目录结构

```text
.
├── index.html                  双端选择入口
├── HANDOFF.md                  本文件
├── desktop/
│   └── index.html              Windows 外壳：导航栏 + 内容区 + Inspector
├── mobile/
│   └── index.html              手机外壳：顶栏 + 内容区 + 底部 5 Tab
└── src/
    ├── core/
    │   ├── data.js             静态数据：模型、产物、诊断项、日志、页面元信息
    │   ├── state.js            运行时状态（单例）与派生值
    │   └── storage.js          localStorage 适配层（默认关闭）
    ├── service/
    │   └── gateway.js          模拟网关服务：启停、签到、额度、刷新、诊断
    ├── ui/
    │   ├── app.js              控制器：路由、事件、弹窗、Toast、状态同步
    │   ├── pages.js            7 个页面模板（双端共用）
    │   ├── components.js       列表项、开关行、卡片等通用模板
    │   └── inspector.js        桌面端源码映射侧栏
    ├── res/
    │   ├── tokens.css          设计令牌（颜色、圆角、字体、动效）
    │   ├── base.css            重置与全局基础
    │   ├── components.css      组件样式（卡片、按钮、开关、弹窗等）
    │   └── icons.js            内联 SVG 图标
    ├── desktop/
    │   ├── layout.css          三栏布局与桌面断点
    │   └── app.js              启动桌面端
    └── mobile/
        ├── layout.css          单列布局、底部 Tab、安全区
        └── app.js              启动手机端 + 返回按钮
```

## 4. 与 Android 源码的映射

| Web 源码 | Android 原工程 | 说明 |
|---|---|---|
| `src/res/tokens.css` | `res/values/colors.xml`、`themes.xml`、`styles.xml` | 品牌蓝 `#0B57D0`、表面层级、圆角、状态色 |
| `src/res/icons.js` | `res/drawable/ic_*.xml` | Material 风格线性图标 |
| `src/ui/pages.js` | `res/layout/activity_*.xml` + `item_*.xml` | 每个函数对应一个页面布局 |
| `src/ui/components.js` | `ui/*Adapter.java`、通用控件 | 列表项与重复组件 |
| `src/ui/app.js` | `ui/MainActivity`、`SettingsActivity` 等 | 路由、事件、弹窗与状态同步 |
| `src/core/state.js` | `core/Store.java`、`AppConfig.java` | 运行时配置与登录态 |
| `src/core/storage.js` | `SharedPreferences` | 可替换为真实持久化 |
| `src/service/gateway.js` | `service/GatewayService`、`core/Actions`、`core/Diagnostics` | 目前是模拟实现 |
| `desktop/` | `desktop/`（Windows 桌面版） | 宽屏三栏形态 |
| `mobile/` | `app/`（Android 手机版） | 单列 + 底部导航形态 |

页面与页面的对应关系：

| 页面 | `pages.js` 函数 | 原布局 |
|---|---|---|
| 总览 | `home` | `activity_main.xml` |
| 登录 | `login` | `activity_login.xml` |
| 模型 | `models` | `activity_models.xml`、`item_model.xml` |
| 设置 | `settings` | `activity_settings.xml` |
| API Key | `keys` | `activity_apikeys.xml` |
| 诊断 | `diag` | `activity_diag.xml` |
| 产物 | `artifacts` | `activity_artifacts.xml`、`item_artifact.xml` |

## 5. 双端差异

两端页面内容与交互相同，差异只在外壳与布局：

| 能力 | Windows 端 | 手机端 |
|---|---|---|
| 导航 | 左侧 96px 固定 Rail，7 项 | 底部 5 个 Tab：总览、模型、诊断、产物、设置 |
| 页面标题 | 内容区 `page-head` 内 | 顶部栏 `pageTitle` |
| 右侧源码映射 | 有，380px Side Sheet | 无 |
| 登录入口 | Rail + 总览按钮 | 总览 Hero 按钮 |
| API Key 入口 | Rail + 总览「更多操作」 | 总览「更多操作」 |
| 内容滚动 | 内容区独立滚动 | 页面滚动，底部留出 Tab 安全区 |
| 登录页页头 | 隐藏 | 隐藏 |

手机端要新增 Tab 时，改 `mobile/index.html` 的 `.m-tabs` 并保证 `data-nav` 在 7 个页面名内即可。

## 6. 数据契约

### 运行时状态 `App.state`

```js
{
  screen: "home",            // 当前页面
  running: false,            // 网关是否运行
  port: 8080,                // 监听端口
  lan: false,                // 是否允许局域网
  loggedIn: true,            // 会话是否有效
  sessionTail: "c9f3a1",     // 会话尾号（仅展示）
  cookie: "...",             // 会话 Cookie
  loginCaptured: false,      // 是否已捕获网页登录
  keys: ["123456"],          // 网关 API Key
  models: [ ... ],           // 模型列表
  query: "",                 // 模型搜索词
  requests: 128,             // 累计请求（演示值）
  devSessions: 2,            // 上游会话数
  balanceText: "",           // 额度查询结果
  welfareText: "",           // 签到结果
  diagItems: [],             // 已完成诊断项
  diagVerdict: "",           // 诊断结论
  diagVerdictClass: "",      // is-ok / is-fail
  logs: [ ... ],             // 日志行
  settings: { ... }          // 设置页开关
}
```

派生值在 `App.derived`：

- `baseUrl()`：根据 `lan` 与 `port` 生成 Base URL
- `loginLabel()`：登录状态文案
- `statusDetail()`：总览状态卡的多行详情

### 模型项

```js
{ id: "GLM-5.2", name: "GLM-5.2", tags: ["深度推理模型"] }
```

### 产物项

```js
{ name, path, meta, kind }
```

### 诊断项

```js
{ name, ok, detail }
```

### 页面元信息 `App.data.SCREEN_META`

每个页面包含 `label`、`lede`、`title`、`files`、`methods`、`notes`、`code`，用于页面标题和桌面端 Inspector。新增页面时同步补充。

## 7. 交互契约

控制器使用事件委托，页面重渲染后无需重新绑定。适配时保持这些属性不变：

| 属性 | 用途 |
|---|---|
| `data-nav="home"` | 切换页面（7 个页面名） |
| `data-action="toggle"` | 启动 / 停止网关 |
| `data-action="checkin"` | 签到 |
| `data-action="balance"` | 查询额度 |
| `data-action="chip-port"` | 修改端口弹窗 |
| `data-action="copy-base"` | 复制 Base URL |
| `data-action="battery"` | 电池白名单（设置页） |
| `data-action="model-refresh"` | 重新拉取模型 |
| `data-action="copy-all"` | 复制全部模型 ID |
| `data-action="clear-search"` | 清空模型搜索 |
| `data-action="save-settings"` | 保存设置 |
| `data-action="settings-login"` | 打开登录选择 |
| `data-action="add-key"` | 添加 API Key |
| `data-action="run-diag"` | 一键体检 |
| `data-action="copy-diag"` | 复制诊断与日志 |
| `data-action="clear-log"` | 清空日志 |
| `data-action="web-login"` | 模拟网页登录 |
| `data-action="save-cookie"` | 保存会话 |
| `data-action="copy-cookie"` | 复制 Cookie |
| `data-action="paste-cookie"` | 粘贴 Cookie |
| `data-action="reload-login"` | 重新加载登录页 |
| `data-action="notify-settings"` | 通知设置 |
| `data-model="ID"` | 点击复制模型 ID |
| `data-copy-key` / `data-del-key` | 密钥复制 / 删除 |
| `data-artifact-open/save/copy` | 产物操作 |
| `data-close` | 关闭弹窗 |

关键 DOM id：`pageTitle`、`pageLede`、`pageCrumb`、`view`、`scrollRoot`、`railDot2`、`railState2`、`tvState`、`dotState`、`btnToggle`、`chipPort`、`tvDetail`、`tvBase`、`tvEndpoints`、`statRequests`、`statSessions`、`statKeys`、`statModels`、`settingsLoginState`、`setPort`、`setLan`、`setAutostart`、`setFloat`、`setWake`、`setNotify`、`setTrim`、`setThinking`、`setLastUser`、`setDeliverables`、`loginStateText`、`keyInput`、`keyList`、`modelSearch`、`modelList`、`modelStatus`、`btnClearSearch`、`diagVerdict`、`diagList`、`diagLog`、`artifactList`、`inspTitle`、`inspBody`、`mBack`。

## 8. 接入真实网关

### 8.1 替换模拟服务

`src/service/gateway.js` 是唯一需要改网络请求的地方。保持方法签名与返回结构：

```js
async start()            // { ok }
async stop()             // { ok }
async checkin()          // { ok, text }
async balance()          // { ok, text }
async refreshModels(cur) // { ok, models }
diagnose(onItem)         // 逐项回调 + Promise<{ allOk }>
```

建议对应真实接口：

- `start/stop` → 调用 Android `GatewayService` 或本地守护进程
- `checkin` → `POST /codearts/ops/delivery`
- `balance` → `GET /snap-manager/v1/statistics/plugin`
- `refreshModels` → `GET /v1/models`
- `diagnose` → 复用 `core/Diagnostics` 的探针，逐项回调

### 8.2 登录与会话

把 `login` 页的静态表单替换为真实 WebView / OAuth 入口。保存会话时只依赖：

```js
state.cookie
state.loggedIn
state.sessionTail
state.loginCaptured
```

并调用 `App.storage.saveCookie()`。真实环境应改为安全存储，不要长期明文保存 Cookie。

### 8.3 持久化

`src/core/storage.js` 默认 `enabled = false`。接入时：

1. 设置 `App.storage.enabled = true`；或
2. 替换为调用宿主提供的键值存储 / 后端接口。

需要持久化的字段：`settings`、`keys`、`cookie`。

### 8.4 真实数据

`src/core/data.js` 中的模型、产物、诊断项、日志都是演示数据。替换时保持数组/对象字段不变即可；页面模板无需改动。

### 8.5 视觉与品牌

- 颜色、圆角、字体：`src/res/tokens.css`
- 组件尺寸与状态：`src/res/components.css`
- 双端布局：`src/desktop/layout.css`、`src/mobile/layout.css`
- 图标：`src/res/icons.js`（或替换为宿主图标组件）

## 9. 设计说明

- 总览 Hero 中，**登录在左、启动在右**，两个按钮等宽同高（48px）。
- 启动后按钮文字变为「停止网关」，并通过 `.is-stop` 切换为红色 `rgb(186, 26, 26)`；停止后恢复品牌蓝。
- 登录页隐藏 `.page-head`，实现全屏登录视图。
- 总览快捷操作保持 5 个核心卡（模型、诊断、产物、设置、更多操作）；签到、额度、API Key 收在「更多操作」卡内。
- 电池优化白名单位于设置页「常驻与保活」。
- 诊断页完成后若存在失败项，结论使用 `is-fail`；全部通过使用 `is-ok`。

## 10. 验收清单

- [ ] Windows 端 7 个页面均可切换，Inspector 跟随更新
- [ ] 手机端 5 个 Tab 可切换，登录页可返回
- [ ] 登录在左、启动在右，两个按钮等宽
- [ ] 启动后按钮变红并显示「停止网关」，停止后恢复蓝色
- [ ] 端口弹窗可校验 1024–65535，运行中修改会模拟重启
- [ ] 模型搜索、刷新、复制 ID 正常
- [ ] 设置页 8 个开关 + 电池白名单入口正常
- [ ] API Key 可添加、复制、删除
- [ ] 诊断逐项出现，结论与日志同步
- [ ] 产物 3 个操作均有反馈
- [ ] 桌面与手机端均无横向溢出
- [ ] 控制台无报错

## 11. 已知限制

- 纯静态原型，无构建工具与依赖。
- 所有网络行为为模拟，不连接真实网关。
- 状态为内存单例，刷新后重置（持久化默认关闭）。
- 模型、诊断、产物、日志为示例数据。
- 手机端底部只有 5 个 Tab；登录与密钥从总览入口进入，如需要可为手机端增加「更多」抽屉。