window.App = window.App || {};

/**
 * Android WebView 桥接层。
 *
 * 页面在浏览器里打开时 window.CodeArtsNative 不存在，所有调用会回退到
 * gateway.js 的模拟实现；在 App 内则由 WebBridge 提供真实数据与操作。
 */
window.App.native = (function () {
  const pending = {};

  window.__nativeCallback = function (id, ok, data) {
    const resolve = pending[id];
    if (!resolve) {
      return;
    }
    delete pending[id];
    let obj = {};
    try {
      obj = data ? JSON.parse(data) : {};
    } catch (error) {
      obj = {};
    }
    resolve(Object.assign({ ok: !!ok }, obj));
  };

  function bridge() {
    return (typeof window.CodeArtsNative !== "undefined"
      && window.CodeArtsNative
      && typeof window.CodeArtsNative.state === "function")
      ? window.CodeArtsNative
      : null;
  }

  function available() {
    return !!bridge();
  }

  function state() {
    const b = bridge();
    if (!b) {
      return null;
    }
    try {
      return JSON.parse(b.state());
    } catch (error) {
      return null;
    }
  }

  function call(action, payload) {
    const b = bridge();
    if (!b || typeof b.callAsync !== "function") {
      return Promise.resolve({ ok: false, offline: true });
    }
    return new Promise((resolve) => {
      const id = "cb" + Date.now() + "_" + Math.random().toString(16).slice(2);
      pending[id] = resolve;
      try {
        b.callAsync(action, JSON.stringify(payload || {}), id);
      } catch (error) {
        delete pending[id];
        resolve({ ok: false, message: String(error) });
      }
    });
  }

  function copy(text) {
    const b = bridge();
    if (b && typeof b.copy === "function") {
      b.copy(String(text || ""));
      return true;
    }
    return false;
  }

  return { available, state, call, copy };
})();