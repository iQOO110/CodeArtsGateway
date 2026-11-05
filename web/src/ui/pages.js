window.App = window.App || {};

window.App.pages = (function () {
  const data = window.App.data;
  const icons = window.App.icons;
  const C = window.App.components;
  const esc = C.esc;

  function filterModels(state) {
    const query = state.query.trim().toLowerCase();
    return state.models.filter((model) =>
      !query || model.id.toLowerCase().includes(query) || model.name.toLowerCase().includes(query)
    );
  }

  function modelStatus(state, shown) {
    const total = state.models.length;
    let text = `共 ${total} 个模型`;
    if (shown.length !== total) {
      text += `，筛选出 ${shown.length} 个`;
    }
    return `${text} · 点击条目复制 ID`;
  }

  const pages = {
    home(state) {
      const running = state.running;
      const base = window.App.derived.baseUrl();
      const keyCount = state.keys.length ? `${state.keys.length}` : "0";
      return `
        <div class="hero-grid">
          <div class="card hero">
            <div class="hero-top">
              <span class="eyebrow">Local gateway</span>
              <button class="chip" id="chipPort" data-action="chip-port">端口 ${state.port}</button>
            </div>
            <div class="hero-state-row">
              <span class="dot ${running ? "is-on" : "is-off"}" id="dotState"></span>
              <strong class="hero-state" id="tvState">${running ? "运行中" : "未运行"}</strong>
            </div>
            <p class="hero-detail" id="tvDetail">${esc(window.App.derived.statusDetail())}</p>
            <div class="hero-actions">
              <a class="btn btn-tonal btn-lg" data-nav="login" href="#login">登录</a>
              <button class="btn btn-primary btn-lg${running ? " is-stop" : ""}" id="btnToggle" data-action="toggle">${running ? "停止网关" : "启动网关"}</button>
            </div>
            <div class="hero-stats">
              <div><b id="statRequests">${state.requests}</b><span>请求</span></div>
              <div><b id="statSessions">${state.devSessions}</b><span>上游会话</span></div>
              <div><b id="statKeys">${keyCount}</b><span>网关密钥</span></div>
              <div><b id="statModels">${state.models.length}</b><span>模型</span></div>
            </div>
          </div>

          <div class="card connect">
            <div class="card-head">
              <h3>客户端接入</h3>
              <span class="badge-soft">OpenAI 兼容</span>
            </div>
            <code class="base" id="tvBase">${esc(base)}</code>
            <pre class="endpoints" id="tvEndpoints">GET  ${esc(base.replace("/v1", "/v1/models"))}
POST ${esc(base.replace("/v1", "/v1/chat/completions"))}</pre>
            <button class="btn btn-outline" data-action="copy-base">${icons.copy()}复制 Base URL</button>
            <p class="support">API Key 未配置时，客户端可填任意值。</p>
          </div>
        </div>

        <h2 class="section-head">快捷操作</h2>
        <div class="action-grid">
          ${C.actionCard("models", icons.models(), "模型列表", `${state.models.length} 个上游模型`)}
          ${C.actionCard("diag", icons.diag(), "诊断与日志", "六项探针一键体检")}
          ${C.actionCard("artifacts", icons.artifacts(), "码道产物", "本轮工作区文件")}
          ${C.actionCard("settings", icons.settings(), "设置", "端口、保活与对话适配")}
          <div class="action action-tools">
            <span class="action-icon">${icons.more()}</span>
            <span class="action-text">
              <b>更多操作</b>
              <span class="mini-actions">
                <button class="mini-action" id="btnCheckin" data-action="checkin">签到</button>
                <button class="mini-action" id="btnBalance" data-action="balance">额度</button>
                <a class="mini-action" data-nav="keys" href="#keys">API Key</a>
              </span>
            </span>
          </div>
        </div>

        <div class="result-grid">
          <pre class="result" id="tvBalance">${esc(state.balanceText)}</pre>
          <pre class="result" id="tvWelfare">${esc(state.welfareText)}</pre>
        </div>

        <p class="footnote">支持 GET /v1/models、POST /v1/chat/completions、GET /health。Base URL 填上方地址，API Key 未配置时任意值。</p>`;
    },

    login(state) {
      const logged = state.loggedIn;
      const tail = state.sessionTail ? `…${state.sessionTail}` : "无";
      return `
        <div class="card">
          <div class="card-head">
            <h3>登录码道 / 更新会话</h3>
            <span class="badge-soft">${logged ? "已登录" : "未登录"}</span>
          </div>
          <p class="support">复用原生登录页：网页登录成功后返回，会话会自动保存并同步到这里。</p>
          <p class="web-state" id="loginStateText">${logged ? `当前会话 ${tail}` : "尚未登录"}</p>
          <button class="btn btn-primary block" data-action="web-login" id="btnWebLogin">打开码道登录页</button>
          <button class="btn btn-tonal block" data-action="save-cookie">保存 / 更新会话</button>
          <button class="btn btn-outline block" data-action="copy-cookie">复制 Cookie</button>
          <button class="btn btn-outline block" data-action="paste-cookie">粘贴 Cookie</button>
          <p class="support">登录页由原生实现，支持网页登录、Cookie 粘贴与历史会话；保存后自动回到新版界面。</p>
        </div>

        <div class="card">
          <h3>当前会话</h3>
          <code class="base">${esc(state.cookie ? `已保存 ${state.cookie.length} 字符 · 尾号 ${state.sessionTail}` : "（无）")}</code>
          <p class="support">Cookie 只保存在本机，用于网关向上游发起请求。</p>
        </div>`;
    },

    models(state) {
      if (!state.loggedIn) {
        return `<div class="empty">尚未登录。请点底部「登录」完成码道网页登录，再回来拉取模型。</div>`;
      }
      const shown = filterModels(state);
      return `
        <div class="toolbar-row">
          <div class="search-field">
            ${icons.search()}
            <input id="modelSearch" placeholder="搜索模型名称或 ID" autocomplete="off" value="${esc(state.query)}">
            <button class="search-clear" id="btnClearSearch" data-action="clear-search"${state.query ? "" : " hidden"} aria-label="清除">×</button>
          </div>
          <button class="btn btn-tonal" data-action="model-refresh">${icons.refresh()}重新拉取</button>
          <button class="btn btn-outline" data-action="copy-all">${icons.copy()}复制全部 ID</button>
        </div>
        <p class="status-line" id="modelStatus">${esc(modelStatus(state, shown))}</p>
        <div class="model-grid" id="modelList">${shown.map((model, index) => C.modelCard(model, index)).join("")}</div>`;
    },

    settings(state) {
      const s = state.settings;
      return `
        <div class="settings-grid">
          <div class="card">
            <h3>网络</h3>
            <label class="tf"><span>监听端口（1024–65535）</span><input id="setPort" inputmode="numeric" value="${state.port}"></label>
            ${C.switchRow("setLan", "允许局域网访问", "不勾选时仅监听 127.0.0.1", state.lan)}
          </div>

          <div class="card">
            <h3>常驻与保活</h3>
            ${C.switchRow("setAutostart", "开机自动启动网关", "", s.autostart)}
            ${C.switchRow("setFloat", "悬浮球保活", "拖动移动，点击启停，长按隐藏", s.floatBall)}
            ${C.switchRow("setWake", "CPU 保活（WakeLock）", "运行期间阻止 CPU 休眠", s.wakeLock)}
            ${C.switchRow("setNotify", "常驻通知保护（前台服务）", "", s.notify)}
            <div class="card-btn-row">
              <button class="btn btn-text" data-action="notify-settings">打开通知设置</button>
              <button class="btn btn-text" data-action="battery">电池优化白名单</button>
            </div>
          </div>

          <div class="card wide">
            <h3>对话适配</h3>
            <div class="setting-grid">
              ${C.switchRow("setTrim", "过滤 agent 注入内容", "只把用户原始输入发给码道", s.trimSystem)}
              ${C.switchRow("setThinking", "透传思考过程", "reasoning_content", s.streamThinking)}
              ${C.switchRow("setLastUser", "每轮只发用户原始输入", "关闭后转发完整历史", s.onlyLastUser)}
              ${C.switchRow("setDeliverables", "透传本轮产物", "文件路径、状态与增删行", s.deliverables)}
            </div>
            <p class="support">这些开关直接改写 GatewayServer 的静态策略字段，下一轮对话生效。</p>
          </div>
        </div>

        <div class="save-bar">
          <span class="support">端口变更或通知渠道切换时，运行中的网关会自动重启。</span>
          <button class="btn btn-primary" data-action="save-settings">保存设置</button>
        </div>`;
    },

    keys(state) {
      const list = state.keys.length
        ? state.keys.map((key) => C.keyRow(key)).join("")
        : `<div class="empty">暂无密钥 —— 客户端可不填 API Key（不校验）。<br>添加后客户端必须携带 Authorization: Bearer &lt;密钥&gt;。</div>`;
      return `
        <div class="card">
          <div class="card-head">
            <h3>网关 API Key</h3>
            <span class="badge-soft">Bearer / x-api-key</span>
          </div>
          <p class="support">默认密钥 123456。列表留空 = 不校验；输入框留空点「添加」会自动生成 <code>sk-codearts-*</code>。</p>
          <div class="key-add">
            <label class="tf"><span>新密钥</span><input id="keyInput" placeholder="sk-codearts-…（留空自动生成）" autocomplete="off"></label>
            <button class="btn btn-primary" data-action="add-key">添加</button>
          </div>
          <div class="key-list" id="keyList">${list}</div>
        </div>`;
    },

    diag(state) {
      const verdictText = state.diagVerdict || "点上方按钮开始体检。检查项会逐条出现，失败项给出具体原因。";
      const verdictClass = state.diagVerdictClass ? ` ${state.diagVerdictClass}` : "";
      const items = state.diagItems.map((item) => C.diagItem(item)).join("");
      return `
        <div class="card diag-hero">
          <div>
            <h3>一键体检</h3>
            <p class="support">本地 Cookie → 上游模型 → 上游签到 → 积分用量 → 对话链路。</p>
          </div>
          <button class="btn btn-primary" data-action="run-diag" id="btnRunDiag">开始体检</button>
        </div>
        <p class="verdict${verdictClass}" id="diagVerdict">${esc(verdictText)}</p>
        <div class="diag-list" id="diagList">${items}</div>
        <div class="card log-card">
          <div class="card-head">
            <h3>详细日志</h3>
            <div class="row-actions">
              <button class="btn btn-text" data-action="copy-diag">复制全部</button>
              <button class="btn btn-text" data-action="download-log">下载日志</button>
              <button class="btn btn-text" data-action="clear-log">清空日志</button>
            </div>
          </div>
          <pre class="log" id="diagLog">${esc(state.logs.join("\n"))}</pre>
        </div>`;
    },

    artifacts(state) {
      const list = (state && state.artifacts) || [];
      return `
        <div class="path-bar">
          <div>
            <span class="eyebrow">存储目录</span>
            <code id="artifactRoot">/sdcard/Download/CodeArtsArtifacts</code>
          </div>
          <span class="support">每轮产物自动落盘，可打开、另存或复制完整路径。</span>
        </div>
        <div class="artifact-grid" id="artifactList">${list.length
          ? list.map((item) => C.artifactCard(item)).join("")
          : `<div class="empty">还没有产物。先在码道生成文件，回到这里下拉刷新。</div>`}</div>`;
    }
  };

  pages.filterModels = filterModels;
  pages.modelStatus = modelStatus;
  return pages;
})();