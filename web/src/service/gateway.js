window.App = window.App || {};

/**
 * 网关服务层：App 内（URL 带 native=1）只认真实 WebBridge 结果，
 * 桥接不可用时直接失败，绝不返回模拟的积分、模型或诊断结果；
 * 浏览器预览（不带 native=1）才使用模拟数据。
 */
window.App.gateway = (function () {
  const data = window.App.data;
  const native = window.App.native;
  const isApp = /[?&]native=1/.test(window.location.search);

  const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

  function now() {
    return new Date().toLocaleTimeString("zh-CN", { hour12: false });
  }

  function hasNative() {
    return !!(native && native.available());
  }

  function canSimulate() {
    return !isApp;
  }

  async function callNative(action, payload) {
    if (!hasNative()) {
      return null;
    }
    return native.call(action, payload);
  }

  return {
    now,
    hasNative,
    isApp,

    async start() {
      const r = await callNative("start");
      if (r) {
        return { ok: r.ok !== false, message: r.message || "" };
      }
      if (!canSimulate()) {
        return { ok: false, message: "原生桥接不可用，请重启 App" };
      }
      await wait(550);
      return { ok: true };
    },

    async stop() {
      const r = await callNative("stop");
      if (r) {
        return { ok: r.ok !== false, message: r.message || "" };
      }
      if (!canSimulate()) {
        return { ok: false, message: "原生桥接不可用，请重启 App" };
      }
      await wait(120);
      return { ok: true };
    },

    async checkin() {
      const r = await callNative("checkin");
      if (r) {
        return { ok: r.ok !== false, text: r.text || r.message || "" };
      }
      if (!canSimulate()) {
        return { ok: false, text: "尚未登录或原生桥接不可用" };
      }
      await wait(1000);
      return {
        ok: true,
        text: "签到结果：签到成功（+20 积分）\n· 每日签到  [daily/claimed]  +20\n· 连续签到  [streak/pending]\n· 模型体验包  [benefit/available]"
      };
    },

    async balance() {
      const r = await callNative("balance");
      if (r) {
        return { ok: r.ok !== false, text: r.text || r.message || "" };
      }
      if (!canSimulate()) {
        return { ok: false, text: "尚未登录或原生桥接不可用" };
      }
      await wait(850);
      return {
        ok: true,
        text: "积分：剩余 8,420 / 10,000（已用 15.8%）\n免费额度：剩余 9,120,000 / 10,000,000（已用 8%）\n套餐 token：1,240,000 / 5,000,000"
      };
    },

    async refreshModels(current) {
      const r = await callNative("refreshModels");
      if (r) {
        return {
          ok: r.ok !== false,
          models: r.models || [],
          message: r.message || ""
        };
      }
      if (!canSimulate()) {
        return { ok: false, models: [], message: "尚未登录或原生桥接不可用" };
      }
      await wait(800);
      return { ok: true, models: current.slice() };
    },

    async diagnose(onItem) {
      if (hasNative()) {
        const r = await native.call("diagnose");
        const items = r.items || [];
        items.forEach((item, index) => onItem(item, index));
        return { allOk: !!r.allOk, items };
      }
      if (!canSimulate()) {
        return { allOk: false, items: [], message: "原生桥接不可用，请重启 App" };
      }
      return new Promise((resolve) => {
        data.DIAG_ITEMS.forEach((item, index) => {
          setTimeout(() => {
            onItem(item, index);
          }, 240 + index * 260);
        });
        setTimeout(() => {
          resolve({ allOk: data.DIAG_ITEMS.every((item) => item.ok) });
        }, 240 + data.DIAG_ITEMS.length * 260);
      });
    }
  };
})();