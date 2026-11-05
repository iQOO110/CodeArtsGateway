window.App = window.App || {};

window.App.state = (function () {
  const data = window.App.data;

  const state = {
    screen: "home",
    running: false,
    port: 8080,
    lan: false,
    loggedIn: false,
    sessionTail: "",
    cookie: "",
    loginCaptured: false,
    keys: [],
    models: [],
    query: "",
    requests: 0,
    devSessions: 0,
    balanceText: "",
    welfareText: "",
    diagItems: [],
    diagVerdict: "",
    diagVerdictClass: "",
    logs: [],
    settings: {
      autostart: true,
      floatBall: false,
      wakeLock: true,
      notify: true,
      trimSystem: true,
      streamThinking: true,
      onlyLastUser: false,
      deliverables: true
    }
  };

  // App 内优先使用 WebBridge 的真实状态
  const nativeState = window.App.native && window.App.native.state();
  if (nativeState) {
    Object.assign(state, nativeState);
    if (nativeState.settings) {
      Object.assign(state.settings, nativeState.settings);
    }
  }

  window.App.derived = {
    baseUrl() {
      const host = state.lan ? (state.lanIp || "192.168.1.42") : "127.0.0.1";
      return `http://${host}:${state.port}/v1`;
    },
    loginLabel() {
      return state.loggedIn
        ? `已登录 · 会话 …${state.sessionTail}`
        : "未登录（点「登录」更新会话）";
    },
    statusDetail() {
      const lines = [
        `DevCloud：${window.App.derived.loginLabel()}`,
        `监听：${state.lan ? "局域网开放（0.0.0.0）" : "仅本机"}`
      ];
      if (state.running) {
        const keyCount = state.keys.length ? `${state.keys.length} 个` : "未配置";
        lines.push(`请求 ${state.requests} · DevCloud 会话 ${state.devSessions} · 网关密钥 ${keyCount}`);
      }
      return lines.join("\n");
    }
  };

  return state;
})();