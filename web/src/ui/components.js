window.App = window.App || {};

window.App.components = (function () {
  const icons = window.App.icons;

  const esc = (value) => String(value == null ? "" : value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");

  return {
    esc,

    actionCard(nav, icon, title, description) {
      return `
        <a class="action" data-nav="${nav}" href="#${nav}">
          <span class="action-icon">${icon}</span>
          <span class="action-text"><b>${esc(title)}</b><small>${esc(description)}</small></span>
        </a>`;
    },

    switchRow(id, title, description, checked) {
      const hint = description ? `<small>${esc(description)}</small>` : "";
      return `
        <div class="setting-row">
          <div class="setting-text">${esc(title)}${hint}</div>
          <label class="switch">
            <input type="checkbox" id="${id}"${checked ? " checked" : ""}>
            <span class="track"></span>
          </label>
        </div>`;
    },

    modelCard(model, index) {
      return `
        <button class="model-card" data-model="${esc(model.id)}">
          <div class="model-card-head">
            <span class="model-name">${esc(model.name)}</span>
            <span class="model-index">#${index + 1}</span>
          </div>
          <code class="model-id">${esc(model.id)}</code>
          <div class="tag-row">${model.tags.map((tag) => `<span class="tag">${esc(tag)}</span>`).join("")}</div>
          <span class="copy-hint">点击复制模型 ID</span>
        </button>`;
    },

    keyRow(key) {
      return `
        <div class="key-row">
          <code>${esc(key)}</code>
          <button class="btn btn-tonal mini" data-copy-key="${esc(key)}">复制</button>
          <button class="btn btn-text mini" data-del-key="${esc(key)}">删除</button>
        </div>`;
    },

    artifactCard(item) {
      return `
        <div class="artifact-card">
          <div class="artifact-top">
            <span class="file-badge">${esc(item.kind)}</span>
            <div>
              <div class="artifact-name">${esc(item.name)}</div>
              <p class="artifact-meta">${esc(item.meta)}</p>
            </div>
          </div>
          <code class="artifact-path">${esc(item.path)}</code>
          <div class="artifact-actions">
            <button class="btn btn-tonal" data-artifact-open="${esc(item.path)}">打开</button>
            <button class="btn btn-tonal" data-artifact-save="${esc(item.path)}">下载 / 另存</button>
            <button class="btn btn-outline" data-artifact-copy="${esc(item.path)}">复制路径</button>
          </div>
        </div>`;
    },

    diagItem(item) {
      return `
        <div class="diag-item is-in">
          <div class="diag-head">
            <span class="dot ${item.ok ? "is-on" : "is-off"}"></span>
            <b>${esc(item.name)}</b>
          </div>
          <p class="diag-detail">${esc(item.detail)}</p>
        </div>`;
    },

    searchIcon() {
      return icons.search();
    }
  };
})();