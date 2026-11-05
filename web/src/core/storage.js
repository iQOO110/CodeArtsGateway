window.App = window.App || {};

window.App.storage = (function () {
  const PREFIX = "codearts.gateway.";

  function read(key, fallback) {
    try {
      const raw = localStorage.getItem(PREFIX + key);
      return raw == null ? fallback : JSON.parse(raw);
    } catch (error) {
      return fallback;
    }
  }

  function write(key, value) {
    try {
      localStorage.setItem(PREFIX + key, JSON.stringify(value));
    } catch (error) {
      /* 存储不可用时保持内存态 */
    }
  }

  return {
    enabled: false,
    loadSettings(fallback) {
      return this.enabled ? read("settings", fallback) : fallback;
    },
    saveSettings(settings) {
      if (this.enabled) {
        write("settings", settings);
      }
    },
    loadKeys(fallback) {
      return this.enabled ? read("keys", fallback) : fallback;
    },
    saveKeys(keys) {
      if (this.enabled) {
        write("keys", keys);
      }
    },
    loadCookie(fallback) {
      return this.enabled ? read("cookie", fallback) : fallback;
    },
    saveCookie(cookie) {
      if (this.enabled) {
        write("cookie", cookie);
      }
    }
  };
})();