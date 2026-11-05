window.App = window.App || {};

window.App.data = (function () {
  const PROJECT = {
    name: "CodeArts 网关",
    en: "CodeArts Gateway",
    version: "v1.9.29",
    versionCode: 57,
    repo: "iQOO110/CodeArtsGateway",
    tagline: "把华为云码道包装成 OpenAI 兼容的本地网关",
    summary: "运行在 Android 上的本地 HTTP 网关：对上游伪装成 CodeArts 网页会话，对下游提供 /v1/chat/completions、/v1/models 等标准接口，并内置 function calling 垫片、工具上游转发与 MCP 客户端。"
  };

  const DESIGN_REFS = [
    {
      name: "Material UI",
      stars: "99.1k",
      note: "组件密度、状态层与表单体系",
      url: "https://github.com/mui/material-ui"
    },
    {
      name: "Material Symbols",
      stars: "52.7k",
      note: "24dp 图标网格与轮廓笔画",
      url: "https://github.com/google/material-design-icons"
    },
    {
      name: "Emil Kowalski Skills",
      stars: "50.1k",
      note: "ease-out、只动 transform / opacity、尊重减弱动效",
      url: "https://github.com/emilkowalski/skills"
    },
    {
      name: "Material Design Lite",
      stars: "32.2k",
      note: "12 列栅格、2dp 卡片阴影、悬浮标签输入框",
      url: "https://github.com/google/material-design-lite"
    },
    {
      name: "Material Web",
      stars: "11.2k",
      note: "M3 token、shape scale、state layer",
      url: "https://github.com/material-components/material-web"
    }
  ];

  const MODELS = [
    { id: "GLM-5.2", name: "GLM-5.2", tags: ["深度推理模型，适合复杂任务与深度思考"] },
    { id: "GLM-5-Air", name: "GLM-5-Air", tags: ["轻量", "低延迟"] },
    { id: "GLM-4.6", name: "GLM-4.6", tags: ["通用对话"] },
    { id: "DeepSeek-V3.2", name: "DeepSeek-V3.2", tags: ["通用对话", "代码生成"] },
    { id: "Qwen3-Coder-480B", name: "Qwen3-Coder-480B", tags: ["代码生成", "长上下文"] },
    { id: "Kimi-K2", name: "Kimi-K2", tags: ["长文本", "Agent"] },
    { id: "sn-glm-5-3", name: "sn-glm-5-3", tags: ["限时福利", "工具调用"] },
    { id: "glm-4-flash", name: "glm-4-flash", tags: ["限时福利"] }
  ];

  const DIAG_ITEMS = [
    {
      name: "本地 Cookie",
      ok: true,
      detail: "已保存，共 412 字符、7 个键：[devclouddevuibjtcftk, cftk, HWWAFSESID, HWWAFSESTIME, ...]"
    },
    {
      name: "cftk（CSRF）",
      ok: true,
      detail: "已找到（值 32 字符），会随请求头 double-submit 发送。"
    },
    {
      name: "上游 模型拉取（agents主源+福利合并）",
      ok: true,
      detail: "获取到 8 个模型"
    },
    {
      name: "上游 /ops/delivery（签到）",
      ok: true,
      detail: "解析出 3 条福利，daily/claimed，streak/pending，benefit/available；诊断报文: /sdcard/Download/codearts-welfare.json"
    },
    {
      name: "上游 积分/套餐用量（snap-manager）",
      ok: true,
      detail: "套餐 CodeArts 体验版，积分 剩余 8,420 / 10,000，已用 1,580，token 1,240,000 / 5,000,000"
    },
    {
      name: "上游 建会话（对话链路）",
      ok: false,
      detail: "返回空 id（上游 429：并发会话已满，请关闭旧会话后重试）"
    }
  ];

  const INITIAL_LOGS = [
    "[09:39:54] service WakeLock acquired · notify=on",
    "[09:39:54] tools MCP 服务器 0 个 · ToolShim=on",
    "[09:40:30] devcloud GET /PromptCenterService/v1/agent-center/agents/detail 200",
    "[09:40:31] devcloud POST /codebaseservice/v1/cloudagent/sessions 200 · session_id=7c1e9b2a",
    "[09:40:58] auth 已加载会话 Cookie（412 chars, cftk=yes）",
    "[09:41:02] service 网关已启动 · port=8080 · lan=false",
    "[09:41:06] gateway GET /v1/models 200 · 8 models · 42ms"
  ];

  const SCREEN_META = {
    home: {
      label: "总览",
      lede: "运行状态、接入地址与快捷操作，一屏掌握网关。",
      title: "主界面",
      files: ["layout/activity_main.xml", "ui/MainActivity.java", "service/GatewayService.java"],
      notes: [
        "标题、状态卡、接入地址卡与功能分组全部来自 activity_main.xml。",
        "状态每 1.5s 轮询一次：<code>refresh()</code> 读取服务运行状态、端口、Cookie 与请求计数。",
        "点端口芯片弹出数字输入框，运行中修改会用新端口自动重启网关。",
        "局域网开启时 Base URL 由网卡 IPv4 拼接，否则固定 127.0.0.1。"
      ],
      methods: ["refresh()", "baseUrl()", "promptPort()", "doCheckin()", "fetchBalance()"],
      code: `<span class="cm">// ui/MainActivity.java:178</span>
  <span class="kw">private void</span> refresh() {
    <span class="kw">boolean</span> running = GatewayService.isRunning();
    btnToggle.setText(running
        ? R.string.btn_close_gateway
        : R.string.btn_open_gateway);
    dotState.setBackgroundTintList(ColorStateList.valueOf(
        getColor(running ? R.color.ok_green : R.color.err_red)));
    tvBase.setText(baseUrl());
  }`
    },

    login: {
      label: "登录",
      lede: "网页登录或粘贴 Cookie，保存会话后即可调用网关。",
      title: "登录 / 会话",
      files: ["layout/activity_login.xml", "ui/LoginActivity.java", "core/Store.java"],
      notes: [
        "源码页面是一个全屏 WebView，右下 FAB 展开保存 / 复制 / 粘贴 / 刷新菜单。",
        "保存时从 Cookie 中识别 <code>cftk</code> 会话键，缺失会提示重新登录。",
        "也支持直接粘贴浏览器 Cookie，校验通过后写入 SharedPreferences。",
        "本原型用静态登录表单代替 WebView，不发起任何网络请求。"
      ],
      methods: ["onCreate()", "saveSession()", "pasteCookie()", "applyCookie()"],
      code: `<span class="cm">// Cookie 形如 name=value; name2=value2</span>
  <span class="kw">String</span> cookie = etPaste.getText().toString().trim();
  <span class="kw">if</span> (!cookie.contains(<span class="str">"cftk"</span>)
      &amp;&amp; !cookie.contains(<span class="str">"session"</span>)) {
    toast(<span class="str">"Cookie 里没有会话键"</span>);
    <span class="kw">return</span>;
  }
  store.setCookie(cookie);`
    },

    models: {
      label: "模型",
      lede: "上游模型目录，支持搜索、刷新与一键复制 ID。",
      title: "模型列表",
      files: ["layout/activity_models.xml", "layout/item_model.xml", "ui/ModelsActivity.java", "ui/ModelAdapter.java", "core/Actions.java"],
      notes: [
        "打开时先用 <code>store.cachedModels()</code> 渲染缓存，再请求上游，避免白屏。",
        "搜索在本地过滤 id 与显示名，状态行同步「共 N 个，筛选出 M 个」。",
        "点击条目复制模型 ID，工具栏支持复制全部与重新拉取。",
        "标签在 <code>Store.ModelInfo</code> 构造时净化，过滤 admin、纯数字与布尔值。"
      ],
      methods: ["refresh()", "filter()", "onPick()", "copyAllIds()"],
      code: `<span class="cm">// ui/ModelAdapter.java:58</span>
  <span class="kw">private void</span> applyFilter() {
    shown.clear();
    <span class="kw">for</span> (Store.ModelInfo m : all) {
      <span class="kw">if</span> (query.isEmpty()
          || m.id.toLowerCase().contains(query)
          || m.name.toLowerCase().contains(query)) {
        shown.add(m);
      }
    }
    notifyDataSetChanged();
  }`
    },

    settings: {
      label: "设置",
      lede: "端口、局域网、保活与对话适配策略。",
      title: "设置",
      files: ["layout/activity_settings.xml", "ui/SettingsActivity.java", "core/Store.java", "service/FloatingBallService.java"],
      notes: [
        "四组卡片：账号、网络、常驻与保活、对话适配，全部由 MaterialSwitch 组成。",
        "保存时校验端口 1024–65535；运行中改端口 / 局域网 / 通知渠道会重启网关。",
        "悬浮球依赖「显示在其他应用上层」权限，未授权会自动回退为关闭。",
        "对话适配开关直接改写 GatewayServer 的静态策略字段，下一轮对话生效。"
      ],
      methods: ["onCreate()", "save()", "showLoginDialog()", "requestOverlayPermission()"],
      code: `<span class="cm">// ui/SettingsActivity.java:141</span>
  <span class="kw">boolean</span> restart = wasRunning
      &amp;&amp; (port != store.getPort()
      || cbLan.isChecked() != store.isLanEnabled());

  GatewayServer.sTrimSystem = store.isTrimSystem();
  GatewayServer.sStreamThinking = store.isStreamThinking();
  GatewayServer.sOnlyLastUser = store.isOnlyLastUser();

  <span class="kw">if</span> (restart) {
    GatewayService.ensureStopped(<span class="kw">this</span>);
    <span class="cm">// 500ms 后以新配置重启</span>
  }`
    },

    keys: {
      label: "密钥",
      lede: "网关侧的 sk-codearts-* 密钥，留空即不校验。",
      title: "API Key",
      files: ["layout/activity_apikeys.xml", "ui/ApiKeysActivity.java", "core/Store.java"],
      notes: [
        "默认写入一个 <code>123456</code> 密钥，客户端也可用 <code>x-api-key</code> 头。",
        "密钥列表为空时网关不做校验，任意值都可通过。",
        "输入框留空点「添加」会生成 <code>sk-codearts-</code> 前缀的随机密钥。",
        "列表项支持复制与删除，删除前有二次确认弹窗。"
      ],
      methods: ["render()", "addApiKey()", "removeApiKey()", "newApiKey()"],
      code: `<span class="cm">// ui/ApiKeysActivity.java:43</span>
  String k = etNew.getText().toString().trim();
  <span class="kw">if</span> (k.isEmpty()) {
    k = Store.newApiKey();
  }
  store.addApiKey(k);
  etNew.setText(<span class="str">""</span>);
  render();
  toast(<span class="str">"已添加密钥"</span>);`
    },

    diag: {
      label: "诊断",
      lede: "六项探针逐项体检，失败项直接给出原因。",
      title: "诊断与日志",
      files: ["layout/activity_diag.xml", "ui/DiagActivity.java", "core/Diagnostics.java", "core/LogRing.java"],
      notes: [
        "一键体检在后台线程执行，逐项展示绿 / 红点与说明。",
        "探针覆盖本地 Cookie、cftk、模型拉取、签到接口、积分用量、建会话。",
        "诊断结论汇总为 verdict，任一失败即为「存在失败项」。",
        "日志来自 <code>LogRing</code> 环形缓冲，页面最多展示最近 200 条、8000 字符。"
      ],
      methods: ["runDiag()", "renderItems()", "renderLog()"],
      code: `<span class="cm">// core/Diagnostics.java:67</span>
  r.items.add(<span class="kw">new</span> Item(<span class="str">"本地 Cookie"</span>, <span class="kw">true</span>,
      <span class="str">"已保存，共 "</span> + cookie.length() + <span class="str">" 字符、"</span>
      + names.size() + <span class="str">" 个键："</span> + names));

  r.items.add(<span class="kw">new</span> Item(<span class="str">"cftk（CSRF）"</span>, !cftk.isEmpty(),
      cftk.isEmpty() ? <span class="str">"Cookie 里没有 cftk 键"</span>
                     : <span class="str">"已找到，会随请求头发送。"</span>));`
    },

    artifacts: {
      label: "产物",
      lede: "每轮工作区文件自动落盘，可打开、另存或复制路径。",
      title: "码道产物",
      files: ["layout/activity_artifacts.xml", "layout/item_artifact.xml", "ui/ArtifactsActivity.java", "core/ArtifactIndex.java"],
      notes: [
        "码道每轮工作区产物自动落盘到 <code>Download/CodeArtsArtifacts</code>。",
        "条目卡片提供打开、另存、复制完整路径三个动作。",
        "打开通过 FileProvider 授予临时读权限；另存走 SAF 文档选择器。",
        "文件被移动或删除时提示复制路径到文件管理器查看。"
      ],
      methods: ["reload()", "open()", "saveAs()", "copyPath()"],
      code: `<span class="cm">// ui/ArtifactsActivity.java:94</span>
  <span class="kw">private void</span> copyPath(ArtifactIndex.Item item) {
    ClipboardManager cm = (ClipboardManager)
        getSystemService(Context.CLIPBOARD_SERVICE);
    <span class="kw">if</span> (cm != null) {
      cm.setPrimaryClip(ClipData.newPlainText(
          <span class="str">"artifact_path"</span>, item.path));
    }
    toast(<span class="str">"完整路径已复制"</span>);
  }`
    },

  };
  return {
    PROJECT, DESIGN_REFS, MODELS, DIAG_ITEMS, INITIAL_LOGS, SCREEN_META
  };
})();
