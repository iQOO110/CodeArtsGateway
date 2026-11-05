window.App = window.App || {};

window.App.inspector = (function () {
  const data = window.App.data;

  const esc = (value) => String(value == null ? "" : value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");

  return {
    render(name) {
      const meta = data.SCREEN_META[name];
      if (!meta) {
        return;
      }
      const title = document.getElementById("inspTitle");
      if (title) {
        title.textContent = meta.title;
      }
      const body = document.getElementById("inspBody");
      if (!body) {
        return;
      }
      body.innerHTML = `
        <div class="insp-block">
          <h4>对应文件</h4>
          <div class="insp-files">${meta.files.map((file) => `<span class="insp-file">${esc(file)}</span>`).join("")}</div>
        </div>
        <div class="insp-block">
          <h4>关键方法</h4>
          <ul class="insp-list">${meta.methods.map((method) => `<li><code>${esc(method)}</code></li>`).join("")}</ul>
        </div>
        <div class="insp-block">
          <h4>拆分说明</h4>
          <ul class="insp-list">${meta.notes.map((note) => `<li>${note}</li>`).join("")}</ul>
        </div>
        <div class="insp-block">
          <h4>源码片段</h4>
          <pre class="insp-code">${meta.code}</pre>
        </div>`;
    }
  };
})();