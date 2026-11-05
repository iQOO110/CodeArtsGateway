window.App = window.App || {};

window.App.controller = (function () {
  const data = window.App.data;
  const state = window.App.state;
  const derived = window.App.derived;
  const gateway = window.App.gateway;
  const native = window.App.native;
  const storage = window.App.storage;
  const pages = window.App.pages;
  const components = window.App.components;
  const icons = window.App.icons;

  let variant = "desktop";
  let toastTimer = null;
  let modalCleanup = null;

  const byId = (id) => document.getElementById(id);

  function setText(id, text) {
    const el = byId(id);
    if (el) {
      el.textContent = text;
    }
  }

  function setDot(id, on) {
    const el = byId(id);
    if (el) {
      el.classList.toggle("is-on", on);
      el.classList.toggle("is-off", !on);
    }
  }

  function toast(message) {
    const el = byId("toast");
    if (!el) {
      return;
    }
    el.textContent = message;
    el.classList.add("is-show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove("is-show"), 2400);
  }

  async function copyText(text, message) {
    if (native && native.copy && native.copy(text)) {
      toast(message || "已复制");
      return;
    }
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else {
        const area = document.createElement("textarea");
        area.value = text;
        area.style.position = "fixed";
        area.style.opacity = "0";
        document.body.appendChild(area);
        area.select();
        document.execCommand("copy");
        area.remove();
      }
      toast(message || "已复制");
    } catch (error) {
      toast("复制失败，请手动选择文本");
    }
  }

  function closeModal() {
    const layer = byId("modalLayer");
    if (!layer) {
      return;
    }
    layer.hidden = true;
    layer.innerHTML = "";
    if (typeof modalCleanup === "function") {
      modalCleanup();
      modalCleanup = null;
    }
  }

  function openModal(html, onMount) {
    const layer = byId("modalLayer");
    if (!layer) {
      return;
    }
    if (!layer.hidden) {
      closeModal();
    }
    layer.innerHTML = `<div class="modal" role="dialog" aria-modal="true">${html}</div>`;
    layer.hidden = false;
    const modal = layer.firstElementChild;
    modal.querySelectorAll("[data-close]").forEach((button) => {
      button.addEventListener("click", closeModal);
    });
    if (typeof onMount === "function") {
      onMount(modal);
    }
    const onKey = (event) => {
      if (event.key === "Escape") {
        closeModal();
      }
    };
    const onBackdrop = (event) => {
      if (event.target === layer) {
        closeModal();
      }
    };
    document.addEventListener("keydown", onKey);
    layer.addEventListener("click", onBackdrop);
    modalCleanup = () => {
      document.removeEventListener("keydown", onKey);
      layer.removeEventListener("click", onBackdrop);
    };
  }

  function pushLog(line) {
    state.logs.push(`[${gateway.now()}] ${line}`);
    if (state.logs.length > 200) {
      state.logs.shift();
    }
    renderLog();
  }

  function renderLog() {
    const log = byId("diagLog");
    if (log) {
      log.textContent = state.logs.length ? state.logs.join("\n") : "（暂无日志）";
      log.scrollTop = log.scrollHeight;
    }
  }

  function syncStatus() {
    const running = state.running;
    const statusText = running ? "运行中" : "未运行";
    setText("railState2", statusText);
    setDot("railDot2", running);
    setText("tvState", statusText);
    setDot("dotState", running);

    const toggle = byId("btnToggle");
    if (toggle) {
      toggle.textContent = running ? "停止网关" : "启动网关";
      toggle.classList.toggle("is-stop", running);
      toggle.disabled = false;
    }

    setText("chipPort", `端口 ${state.port}`);
    setText("tvDetail", derived.statusDetail());

    const base = derived.baseUrl();
    setText("tvBase", base);
    setText("tvEndpoints", `GET  ${base.replace("/v1", "/v1/models")}\nPOST ${base.replace("/v1", "/v1/chat/completions")}`);
    setText("statRequests", String(state.requests));
    setText("statSessions", String(state.devSessions));
    setText("statKeys", String(state.keys.length));
    setText("statModels", String(state.models.length));
    setText("settingsLoginState", derived.loginLabel());
  }

  function syncNative() {
    if (!native || !native.available()) {
      return;
    }
    const ns = native.state();
    if (!ns) {
      return;
    }
    Object.assign(state, ns);
    if (ns.settings) {
      Object.assign(state.settings, ns.settings);
    }
    syncStatus();
    const stats = {
      statRequests: state.requests,
      statSessions: state.devSessions,
      statKeys: state.keys.length,
      statModels: state.models.length
    };
    Object.entries(stats).forEach(([id, value]) => {
      const el = byId(id);
      if (el) {
        el.textContent = String(value);
      }
    });
  }

  function updateNav(name) {
    document.querySelectorAll(".rail-item[data-nav], .m-tab[data-nav]").forEach((el) => {
      el.classList.toggle("is-active", el.dataset.nav === name);
    });
  }

  function navigate(name, updateHash = true) {
    if (!data.SCREEN_META[name]) {
      name = "home";
    }
    state.screen = name;
    document.body.dataset.page = name;

    const meta = data.SCREEN_META[name];
    setText("pageTitle", meta.label);
    setText("pageLede", meta.lede);
    setText("pageCrumb", meta.label);

    const view = byId("view");
    if (view) {
      view.innerHTML = pages[name](state);
    }
    if (window.App.inspector) {
      window.App.inspector.render(name);
    }
    updateNav(name);

    const back = byId("mBack");
    if (back) {
      back.hidden = name === "home";
    }

    const scrollRoot = byId("scrollRoot");
    if (scrollRoot) {
      scrollRoot.scrollTop = 0;
    } else {
      window.scrollTo(0, 0);
    }

    if (updateHash) {
      history.replaceState(null, "", `#${name}`);
    }
  }

  function renderModelList() {
    const list = byId("modelList");
    if (!list) {
      return;
    }
    const shown = pages.filterModels(state);
    list.innerHTML = shown.length
      ? shown.map((model, index) => components.modelCard(model, index)).join("")
      : `<div class="empty">没有匹配的模型。换个关键词，或重新拉取。</div>`;
    setText("modelStatus", pages.modelStatus(state, shown));
    const clear = byId("btnClearSearch");
    if (clear) {
      clear.hidden = !state.query;
    }
  }

  function renderKeys() {
    const list = byId("keyList");
    if (!list) {
      return;
    }
    list.innerHTML = state.keys.length
      ? state.keys.map((key) => components.keyRow(key)).join("")
      : `<div class="empty">暂无密钥 —— 客户端可不填 API Key（不校验）。<br>添加后客户端必须携带 Authorization: Bearer &lt;密钥&gt;。</div>`;
  }

  function promptPort() {
    openModal(`
      <div class="modal-head">
        <h3>自定义端口</h3>
        <button class="modal-x" data-close aria-label="关闭">×</button>
      </div>
      <p class="modal-copy">范围 1024–65535。保存后若网关正在运行，会用新端口自动重启。</p>
      <input class="field" id="modalPort" inputmode="numeric" value="${state.port}">
      <div class="modal-actions">
        <button class="btn btn-text" data-close>取消</button>
        <button class="btn btn-primary" id="modalPortSave">保存并应用</button>
      </div>
    `, (modal) => {
      const input = modal.querySelector("#modalPort");
      input.focus();
      input.select();
      const save = () => {
        const value = input.value.trim();
        const port = Number(value);
        if (!value || !Number.isInteger(port)) {
          toast("端口必须是数字");
          return;
        }
        if (port < 1024 || port > 65535) {
          toast("端口需在 1024–65535 之间");
          return;
        }
        const wasRunning = state.running;
        state.port = port;
        const portField = byId("setPort");
        if (portField) {
          portField.value = String(port);
        }
        closeModal();
        syncStatus();
        if (wasRunning) {
          toast(`端口已设为 ${port}，网关重启中…`);
          state.running = false;
          syncStatus();
          setTimeout(() => {
            state.running = true;
            syncStatus();
            pushLog(`service 网关已用新端口重启 · port=${port}`);
            toast("网关已在新端口运行");
          }, 750);
        } else {
          toast(`端口已设为 ${port}`);
        }
      };
      modal.querySelector("#modalPortSave").addEventListener("click", save);
      input.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
          save();
        }
      });
    });
  }

  function openPasteCookie() {
    openModal(`
      <div class="modal-head">
        <h3>粘贴 Cookie</h3>
        <button class="modal-x" data-close aria-label="关闭">×</button>
      </div>
      <p class="modal-copy">把浏览器里码道的完整 Cookie 粘贴到这里，需包含 cftk 或 session。</p>
      <textarea class="field" id="modalCookie" placeholder="name=value; name2=value2">${components.esc(state.loggedIn ? state.cookie : "")}</textarea>
      <div class="modal-actions">
        <button class="btn btn-text" data-close>取消</button>
        <button class="btn btn-primary" id="modalCookieSave">保存会话</button>
      </div>
    `, (modal) => {
      const input = modal.querySelector("#modalCookie");
      input.focus();
      modal.querySelector("#modalCookieSave").addEventListener("click", () => {
        const value = input.value.trim();
        if (!value) {
          toast("请先粘贴 Cookie");
          return;
        }
        if (!/cftk|session/i.test(value)) {
          toast("Cookie 里没有会话键（cftk/session），请确认已登录并复制完整");
          return;
        }
        if (native && native.available()) {
          native.call("saveCookie", { cookie: value }).then((r) => {
            if (r.ok === false) {
              toast(r.message || "Cookie 无效");
              return;
            }
            closeModal();
            syncNative();
            pushLog("auth 已保存粘贴的会话 Cookie（cftk=yes）");
            toast("会话已保存");
          });
          return;
        }
        state.cookie = value;
        state.loggedIn = true;
        state.sessionTail = value.slice(-6);
        storage.saveCookie(value);
        closeModal();
        syncStatus();
        pushLog("auth 已保存粘贴的会话 Cookie（cftk=yes）");
        toast("会话已保存");
      });
    });
  }

  function openLoginChooser() {
    openModal(`
      <div class="modal-head">
        <h3>登录 CodeArts 码道</h3>
        <button class="modal-x" data-close aria-label="关闭">×</button>
      </div>
      <p class="modal-copy">选择网页登录，或直接粘贴浏览器中的 Cookie。</p>
      <div class="modal-actions">
        <button class="btn btn-outline" id="choosePaste">粘贴 Cookie</button>
        <button class="btn btn-primary" id="chooseWeb">打开登录页</button>
      </div>
    `, (modal) => {
      modal.querySelector("#choosePaste").addEventListener("click", () => {
        closeModal();
        navigate("login");
        openPasteCookie();
      });
      modal.querySelector("#chooseWeb").addEventListener("click", () => {
        closeModal();
        navigate("login");
      });
    });
  }

  function confirmDeleteKey(key) {
    openModal(`
      <div class="modal-head">
        <h3>删除密钥</h3>
        <button class="modal-x" data-close aria-label="关闭">×</button>
      </div>
      <p class="modal-copy"><code>${components.esc(key)}</code></p>
      <div class="modal-actions">
        <button class="btn btn-text" data-close>取消</button>
        <button class="btn btn-error" id="confirmDeleteKey">删除</button>
      </div>
    `, (modal) => {
      modal.querySelector("#confirmDeleteKey").addEventListener("click", () => {
        closeModal();
        if (native && native.available()) {
          native.call("removeKey", { key }).then((r) => {
            if (r.ok === false) {
              toast(r.message || "删除失败");
              return;
            }
            syncNative();
            renderKeys();
            syncStatus();
            toast("已删除密钥");
          });
          return;
        }
        state.keys = state.keys.filter((item) => item !== key);
        storage.saveKeys(state.keys);
        renderKeys();
        syncStatus();
        toast("已删除密钥");
      });
    });
  }

  async function handleToggle(el) {
    if (state.running) {
      await gateway.stop();
      state.running = false;
      syncStatus();
      syncNative();
      pushLog("service 网关已停止");
      toast("网关已停止");
      return;
    }
    el.disabled = true;
    el.textContent = "启动中…";
    const result = await gateway.start();
    state.running = result.ok !== false;
    syncStatus();
    syncNative();
    pushLog(`service 网关已启动 · port=${state.port} · lan=${state.lan}`);
    toast(`网关已启动 · ${derived.baseUrl().replace("/v1", "")}`);
  }

  async function handleCheckin(el) {
    if (!state.loggedIn) {
      toast("请先登录 DevCloud");
      return;
    }
    el.disabled = true;
    const output = byId("tvWelfare");
    if (output) {
      output.textContent = "正在签到…";
    }
    const result = await gateway.checkin();
    state.welfareText = result.text;
    if (output) {
      output.textContent = result.text;
    }
    el.disabled = false;
    syncNative();
    if (result.ok === false) {
      pushLog("checkin 未完成：" + (result.text || "未知原因"));
      toast(result.text || "签到失败");
      return;
    }
    pushLog("checkin 已完成");
    toast("签到完成");
  }

  async function handleBalance(el) {
    if (!state.loggedIn) {
      toast("请先登录 DevCloud");
      return;
    }
    el.disabled = true;
    const output = byId("tvBalance");
    if (output) {
      output.textContent = "正在查询额度…";
    }
    const result = await gateway.balance();
    state.balanceText = result.text;
    if (output) {
      output.textContent = result.text;
    }
    el.disabled = false;
    syncNative();
    if (result.ok === false) {
      pushLog("balance 查询失败：" + (result.text || "未知原因"));
      toast(result.text || "额度查询失败");
      return;
    }
    pushLog("balance 查询完成");
  }

  async function handleModelRefresh(el) {
    if (!state.loggedIn) {
      toast("请先登录码道");
      return;
    }
    el.disabled = true;
    const status = byId("modelStatus");
    if (status) {
      status.textContent = "正在从 DevCloud 拉取模型…";
    }
    const result = await gateway.refreshModels(state.models);
    state.models = result.models || state.models;
    renderModelList();
    el.disabled = false;
    syncNative();
    if (result.ok === false) {
      toast(result.message || "刷新失败，请检查登录状态");
      return;
    }
    pushLog("models 拉取完成 · " + state.models.length + " 个");
    toast("已刷新");
  }

  function handleSaveSettings() {
    const portField = byId("setPort");
    const portValue = portField ? portField.value.trim() : String(state.port);
    const port = Number(portValue);
    if (!portValue || !Number.isInteger(port)) {
      toast("端口必须是数字");
      return;
    }
    if (port < 1024 || port > 65535) {
      toast("端口需在 1024–65535 之间");
      return;
    }

    const read = (id, fallback) => {
      const el = byId(id);
      return el ? el.checked : fallback;
    };

    const wasRunning = state.running;
    const lan = read("setLan", state.lan);
    const restart = wasRunning && (port !== state.port || lan !== state.lan);

    state.port = port;
    state.lan = lan;
    state.settings = {
      autostart: read("setAutostart", state.settings.autostart),
      floatBall: read("setFloat", state.settings.floatBall),
      wakeLock: read("setWake", state.settings.wakeLock),
      notify: read("setNotify", state.settings.notify),
      trimSystem: read("setTrim", state.settings.trimSystem),
      streamThinking: read("setThinking", state.settings.streamThinking),
      onlyLastUser: read("setLastUser", state.settings.onlyLastUser),
      deliverables: read("setDeliverables", state.settings.deliverables)
    };
    if (native && native.available()) {
      syncStatus();
      native.call("saveSettings", { port, lan, settings: state.settings }).then((r) => {
        if (r.ok === false) {
          toast(r.message || "保存失败");
          return;
        }
        syncNative();
        toast("已保存");
      });
      return;
    }

    storage.saveSettings(state.settings);
    syncStatus();

    if (restart) {
      state.running = false;
      syncStatus();
      toast("已保存，网关正以新配置重启…");
      pushLog("service 配置变更，网关重启中");
      setTimeout(() => {
        state.running = true;
        syncStatus();
        pushLog(`service 网关已重启 · port=${state.port} · lan=${state.lan}`);
      }, 750);
    } else {
      toast("已保存");
    }
  }

  async function handleAddKey() {
    const input = byId("keyInput");
    if (!input) {
      return;
    }
    let key = input.value.trim();
    if (!key) {
      const alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
      key = "sk-codearts-" + Array.from({ length: 24 }, () =>
        alphabet[Math.floor(Math.random() * alphabet.length)]
      ).join("");
    }
    if (state.keys.includes(key)) {
      toast("该密钥已存在");
      return;
    }
    if (native && native.available()) {
      const r = await native.call("addKey", { key });
      if (r.ok === false) {
        toast(r.message || "添加失败");
        return;
      }
      input.value = "";
      syncNative();
      renderKeys();
      syncStatus();
      toast("已添加密钥");
      return;
    }
    state.keys.push(key);
    input.value = "";
    renderKeys();
    storage.saveKeys(state.keys);
    syncStatus();
    pushLog("auth 新增网关 API Key");
    toast("已添加密钥");
  }

  async function handleRunDiag(el) {
    el.disabled = true;
    state.diagItems = [];
    state.diagVerdict = "正在检测（本机 Cookie → 上游模型 → 上游签到 → 对话链路）…";
    state.diagVerdictClass = "";

    const verdict = byId("diagVerdict");
    if (verdict) {
      verdict.textContent = state.diagVerdict;
      verdict.classList.remove("is-ok", "is-fail");
    }
    const list = byId("diagList");
    if (list) {
      list.innerHTML = "";
    }

    if (native && native.available()) {
      const r = await native.call("diagnose");
      state.diagItems = r.items || [];
      const allOk = !!r.allOk;
      state.diagVerdict = allOk
        ? "全部通过：Cookie 有效、上游接口可达，可以正常使用网关与签到。"
        : "存在失败项：看上面红叉项的说明；多数是 Cookie 失效或不完整，重新登录可解。";
      state.diagVerdictClass = allOk ? "is-ok" : "is-fail";
      syncNative();
      navigate("diag", false);
      el.disabled = false;
      toast("体检完成");
      return;
    }

    await gateway.diagnose((item) => {
      state.diagItems.push(item);
      const target = byId("diagList");
      if (!target) {
        return;
      }
      const holder = document.createElement("div");
      holder.innerHTML = components.diagItem(item);
      const node = holder.firstElementChild;
      target.appendChild(node);
      requestAnimationFrame(() => node.classList.add("is-in"));
    });

    const allOk = data.DIAG_ITEMS.every((item) => item.ok);
    state.diagVerdict = allOk
      ? "全部通过：Cookie 有效、上游接口可达，可以正常使用网关与签到。"
      : "存在失败项：看上面红叉项的说明；多数是 Cookie 失效或不完整，重新登录可解。";
    state.diagVerdictClass = allOk ? "is-ok" : "is-fail";

    const verdictEl = byId("diagVerdict");
    if (verdictEl) {
      verdictEl.textContent = state.diagVerdict;
      verdictEl.classList.add(state.diagVerdictClass);
    }
    el.disabled = false;
    pushLog("diag 体检完成 · 5 项通过 / 1 项失败");
    toast("体检完成");
  }

  async function handleLogout() {
    if (!native || !native.available()) {
      toast("仅 App 内支持退出登录");
      return;
    }
    const r = await native.call("logout");
    if (r.ok === false) {
      toast(r.message || "退出失败");
      return;
    }
    syncNative();
    toast("已退出登录，会话与模型缓存已清除");
    navigate("settings", false);
  }

  async function handleSaveCookie() {
    if (native && native.available()) {
      const r = await native.call("saveCookie");
      if (r.ok === false) {
        toast(r.message || "还没有可保存的会话，请先网页登录");
        return;
      }
      syncNative();
      pushLog("auth 已保存会话 Cookie");
      toast("已保存会话");
      setTimeout(() => navigate("home"), 500);
      return;
    }
    if (!state.loginCaptured && !state.loggedIn) {
      toast("还没有捕获到会话，请先登录");
      return;
    }
    state.cookie = "devclouddevuibjtcftk=ZGVtby1zZXNzaW9u; cftk=demo-csrf-token; HWWAFSESID=0000000000";
    state.loggedIn = true;
    state.sessionTail = "c9f3a1";
    state.loginCaptured = false;
    storage.saveCookie(state.cookie);
    syncStatus();
    pushLog("auth 已保存网页登录会话（cftk=yes）");
    toast("已保存会话");
    setTimeout(() => navigate("home"), 500);
  }

  function handleAction(action, el) {
    switch (action) {
      case "toggle":
        handleToggle(el);
        break;
      case "checkin":
        handleCheckin(el);
        break;
      case "balance":
        handleBalance(el);
        break;
      case "chip-port":
        promptPort();
        break;
      case "copy-base":
        copyText(derived.baseUrl(), `已复制：${derived.baseUrl()}`);
        break;
      case "battery":
        if (native && native.available()) {
          native.call("battery");
        } else {
          toast("已加入电池优化白名单（原型模拟）");
        }
        pushLog("battery 已发起电池优化白名单申请");
        break;
      case "model-refresh":
        handleModelRefresh(el);
        break;
      case "copy-all":
        copyText(state.models.map((model) => model.id).join("\n"), `已复制 ${state.models.length} 个模型 ID`);
        break;
      case "clear-search":
        state.query = "";
        const search = byId("modelSearch");
        if (search) {
          search.value = "";
          search.focus();
        }
        renderModelList();
        break;
      case "save-settings":
        handleSaveSettings();
        break;
      case "settings-login":
        if (native && native.available()) {
          native.call("openLogin");
        } else {
          openLoginChooser();
        }
        break;
      case "logout":
        handleLogout();
        break;
      case "download-log":
        if (native && native.available()) {
          native.call("downloadLog").then((r) => {
            toast(r.ok ? ("日志已保存：" + (r.path || "")) : (r.message || "下载失败"));
          });
        } else {
          toast("仅 App 内支持下载日志");
        }
        break;
      case "add-key":
        handleAddKey();
        break;
      case "run-diag":
        handleRunDiag(el);
        break;
      case "copy-diag":
        copyText(`${state.diagVerdict}\n\n${state.logs.join("\n")}`, "诊断与日志已复制");
        break;
      case "clear-log":
        if (native && native.available()) {
          native.call("clearLog").then(() => syncNative());
        }
        state.logs = [];
        renderLog();
        toast("日志已清空");
        break;
      case "web-login":
        handleWebLogin(el);
        break;
      case "save-cookie":
        handleSaveCookie();
        break;
      case "copy-cookie":
        copyText(state.cookie, "Cookie 已复制");
        break;
      case "paste-cookie":
        openPasteCookie();
        break;
      case "reload-login":
        state.loginCaptured = false;
        setText("loginStateText", "尚未捕获会话");
        toast("页面已重新加载");
        break;
      case "notify-settings":
        if (native && native.available()) {
          native.call("notifySettings");
        } else {
          toast("已跳转通知设置（原型模拟）");
        }
        break;
      default:
        break;
    }
  }

  async function handleWebLogin(el) {
    if (native && native.available()) {
      el.disabled = true;
      await native.call("openLogin");
      el.disabled = false;
      return;
    }
    el.disabled = true;
    const label = el.textContent;
    el.textContent = "登录中…";
    await new Promise((resolve) => setTimeout(resolve, 850));
    state.loginCaptured = true;
    el.disabled = false;
    el.textContent = label;
    setText("loginStateText", "登录成功 · 已捕获会话 Cookie\ncftk=32 字符 · 点「保存会话」写回网关");
    pushLog("auth 网页登录成功，已捕获 cftk");
    toast("登录成功，已捕获会话");
  }

  function bind() {
    document.addEventListener("click", (event) => {
      const nav = event.target.closest("[data-nav]");
      if (nav) {
        event.preventDefault();
        if (nav.dataset.nav === "login" && native && native.available()) {
          native.call("openLogin");
          return;
        }
        navigate(nav.dataset.nav);
        return;
      }

      const actionEl = event.target.closest("[data-action]");
      if (actionEl) {
        event.preventDefault();
        handleAction(actionEl.dataset.action, actionEl);
        return;
      }

      const model = event.target.closest("[data-model]");
      if (model) {
        copyText(model.dataset.model, `已复制模型 ID：${model.dataset.model}`);
        return;
      }

      const copyKey = event.target.closest("[data-copy-key]");
      if (copyKey) {
        copyText(copyKey.dataset.copyKey, "密钥已复制");
        return;
      }

      const delKey = event.target.closest("[data-del-key]");
      if (delKey) {
        confirmDeleteKey(delKey.dataset.delKey);
        return;
      }

      const artifactOpen = event.target.closest("[data-artifact-open]");
      if (artifactOpen) {
        if (native && native.available()) {
          native.call("openArtifact", { path: artifactOpen.dataset.artifactOpen });
        } else {
          toast(`正在用系统应用打开：${artifactOpen.dataset.artifactOpen}`);
        }
        return;
      }

      const artifactSave = event.target.closest("[data-artifact-save]");
      if (artifactSave) {
        if (native && native.available()) {
          native.call("saveArtifact", { path: artifactSave.dataset.artifactSave });
        } else {
          toast(`已另存：${artifactSave.dataset.artifactSave}`);
        }
        return;
      }

      const artifactCopy = event.target.closest("[data-artifact-copy]");
      if (artifactCopy) {
        copyText(artifactCopy.dataset.artifactCopy, "完整路径已复制");
        return;
      }

      if (event.target.closest("[data-close]")) {
        closeModal();
      }
    });

    document.addEventListener("input", (event) => {
      if (event.target.id === "modelSearch") {
        state.query = event.target.value;
        renderModelList();
      }
    });

    window.addEventListener("hashchange", () => {
      const name = location.hash.replace("#", "") || "home";
      if (name !== state.screen) {
        navigate(name, false);
      }
    });
  }

  function init(options) {
    variant = (options && options.variant) || "desktop";
    const ns = native && native.state();
    if (ns) {
      Object.assign(state, ns);
      if (ns.settings) {
        Object.assign(state.settings, ns.settings);
      }
    } else {
      state.settings = storage.loadSettings(state.settings);
      state.keys = storage.loadKeys(state.keys);
      state.cookie = storage.loadCookie(state.cookie);
    }

    bind();

    const initial = location.hash.replace("#", "") || "home";
    navigate(data.SCREEN_META[initial] ? initial : "home", false);
    syncStatus();

    window.__nativeRefresh = function () {
      syncNative();
      navigate(state.screen, false);
    };
    if (native && native.available()) {
      setInterval(syncNative, 2000);
    }

    return variant;
  }

  return {
    init,
    navigate,
    toast,
    copyText
  };
})();