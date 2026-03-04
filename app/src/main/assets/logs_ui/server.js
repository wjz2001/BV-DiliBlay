// 网页服务相关逻辑：请求日志列表、渲染卡片、触发下载/生成
//
// 约定后端接口：
// - GET /api/logs/list -> JSON Array [{ name, size, lastModified, type }]
// - GET /api/logs/{filename} -> 下载（保持原文件名）
// - GET /api/logs/create-manual-and-download -> 生成并下载

(function () {
  "use strict";

  function $(id) {
    return document.getElementById(id);
  }

  function inferTypeFromName(name) {
    if (typeof name !== "string") return "unknown";
    if (name.startsWith("logs_manual_")) return "manual";
    if (name.startsWith("logs_crash_")) return "crash";
    return "unknown";
  }

  // 显示名称：保存的日期时间 + .log
  // 例：logs_manual_2026-02-20_18:01:02.log -> 2026-02-20 18:01:02.log
  function formatDisplayName(name) {
    if (typeof name !== "string") return "";

    var s = name;
    if (s.startsWith("logs_manual_")) s = s.substring("logs_manual_".length);
    if (s.startsWith("logs_crash_")) s = s.substring("logs_crash_".length);

    // 期望显示：2026/02/20 18:01:02.log
    // 原始文件名：logs_manual_2026-02-20_18:01:02.log
    s = s.replace(/^(\d{4})-(\d{2})-(\d{2})/, "$1/$2/$3");
    // 用空格替换日期与时间之间的下划线（并保留 .log）
    s = s.replace(/_/g, " ");
    return s;
  }

  async function fetchLogList() {
    var resp = await fetch("/api/logs/list", { cache: "no-store" });
    if (!resp.ok) {
      var text = "";
      try {
        text = await resp.text();
      } catch (e) {
        text = "";
      }
      throw new Error("list failed: " + resp.status + (text ? " " + text : ""));
    }
    var data = await resp.json();
    return Array.isArray(data) ? data : [];
  }

  function clearNode(node) {
    while (node.firstChild) node.removeChild(node.firstChild);
  }

  function makeLogCard(item) {
    var originalName = item && item.name ? String(item.name) : "";
    var displayName = formatDisplayName(originalName);

    var card = document.createElement("mdui-card");
    card.className = "log-card";
    card.setAttribute("clickable", "");
    // 按你的要求：mdui-card 不设置 href（只保留 clickable），点击卡片本身不触发下载

    // 只有点击左侧下载按钮才下载对应日志（保持原文件名）
    var downloadBtn = document.createElement("mdui-button-icon");
    downloadBtn.className = "download-btn";
    downloadBtn.setAttribute("icon", "download");
    downloadBtn.setAttribute("selectable", "");
    downloadBtn.setAttribute("href", "/api/logs/" + encodeURIComponent(originalName));
    downloadBtn.setAttribute("download", originalName);

    // 方案 C：三块布局（左/标题/右），左右 flex:1 等分剩余空间，让标题自然居中
    var bar = document.createElement("div");
    bar.className = "bar";

    var left = document.createElement("div");
    left.className = "side side--left";
    left.appendChild(downloadBtn);

    var title = document.createElement("div");
    title.className = "log-name";
    title.textContent = displayName || originalName;

    var right = document.createElement("div");
    right.className = "side side--right";
    right.setAttribute("aria-hidden", "true");

    bar.appendChild(left);
    bar.appendChild(title);
    bar.appendChild(right);
    card.appendChild(bar);

    return card;
  }

  function renderList(container, items) {
    clearNode(container);
    for (var i = 0; i < items.length; i++) {
      container.appendChild(makeLogCard(items[i]));
    }
  }

  function splitItems(items) {
    var manual = [];
    var crash = [];

    for (var i = 0; i < items.length; i++) {
      var it = items[i] || {};
      var type = it.type || inferTypeFromName(it.name);
      if (type === "manual") manual.push(it);
      else if (type === "crash") crash.push(it);
    }

    // 后端通常已按时间倒序，这里不强依赖；如果字段存在则再保险排序一次
    manual.sort(function (a, b) {
      return (b.lastModified || 0) - (a.lastModified || 0);
    });
    crash.sort(function (a, b) {
      return (b.lastModified || 0) - (a.lastModified || 0);
    });

    return { manual: manual, crash: crash };
  }

  var state = {
    manualListId: null,
    crashListId: null,
  };

  async function refresh() {
    var manualEl = $(state.manualListId);
    var crashEl = $(state.crashListId);
    if (!manualEl || !crashEl) return;

    var items = await fetchLogList();
    var split = splitItems(items);
    renderList(manualEl, split.manual);
    renderList(crashEl, split.crash);
  }

  async function createManualAndDownload() {
    // 用新窗口触发下载，避免当前页面被文件响应替换
    window.open("/api/logs/create-manual-and-download", "_blank");
    // 稍后刷新列表，展示新生成的 logs_manual_*.log
    setTimeout(function () {
      refresh().catch(function () { });
    }, 1500);
  }

  function init(opts) {
    state.manualListId = opts && opts.manualListId ? opts.manualListId : "manualLogList";
    state.crashListId = opts && opts.crashListId ? opts.crashListId : "crashLogList";

    refresh().catch(function () { });
  }

  window.LogService = {
    init: init,
    refresh: refresh,
    createManualAndDownload: createManualAndDownload
  };
})();
