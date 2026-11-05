window.App = window.App || {};

window.App.icons = (function () {
  const wrap = (body) => `<svg viewBox="0 0 24 24">${body}</svg>`;

  return {
    login: () => wrap('<path d="M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4"/><path d="M10 8l-4 4 4 4M6 12h10"/>'),
    models: () => wrap('<path d="M12 3 3 8l9 5 9-5-9-5z"/><path d="M3 12l9 5 9-5"/>'),
    diag: () => wrap('<path d="M3 12h3l2.5-6 3.5 12 2.5-6H21"/>'),
    artifacts: () => wrap('<path d="M3.5 8.5h5l1.6 2h10.4v8a1.5 1.5 0 0 1-1.5 1.5H5a1.5 1.5 0 0 1-1.5-1.5z"/><path d="M3.5 8.5V6a1.5 1.5 0 0 1 1.5-1.5h4l1.6 2H19a1.5 1.5 0 0 1 1.5 1.5v2"/>'),
    settings: () => wrap('<path d="M4 7h10M18 7h2M4 17h4M12 17h8"/><circle cx="16" cy="7" r="2.2"/><circle cx="10" cy="17" r="2.2"/>'),
    keys: () => wrap('<circle cx="8" cy="12" r="3.4"/><path d="M11 12h10M17 12v3M20 12v2"/>'),
    search: () => wrap('<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/>'),
    copy: () => wrap('<rect x="9" y="9" width="10" height="10" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h8"/>'),
    refresh: () => wrap('<path d="M20 11a8 8 0 1 0-2.3 6.3"/><path d="M20 4v7h-7"/>'),
    check: () => wrap('<path d="M5 12.5 9.5 17 19 7.5"/>'),
    wallet: () => wrap('<path d="M4 19V9M10 19V5M16 19v-7M22 19H2"/>'),
    battery: () => wrap('<rect x="3" y="8" width="15" height="9" rx="2"/><path d="M21 11v3"/><path d="m8 10 3 2-3 2"/>'),
    more: () => wrap('<circle cx="5" cy="12" r="1.3" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none"/><circle cx="19" cy="12" r="1.3" fill="currentColor" stroke="none"/>'),
    back: () => wrap('<path d="M15 19 8 12l7-7"/>'),
    home: () => wrap('<path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z"/>'),
    close: () => wrap('<path d="M6 6l12 12M18 6 6 18"/>')
  };
})();