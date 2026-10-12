(() => {
  "use strict";
  const article = document.querySelector(".main-content");
  if (!article) return;
  const sidebar = document.querySelector(".docs-sidebar");
  const menuButton = document.querySelector(".menu-toggle");
  const mobile = window.matchMedia("(max-width: 760px)");
  const themeButton = document.querySelector(".theme-toggle");

  function updateThemeButton() {
    const dark = document.documentElement.dataset.theme !== "light";
    themeButton.setAttribute("aria-label", dark ? "切换到浅色主题" : "切换到深色主题");
    themeButton.title = themeButton.getAttribute("aria-label");
  }
  themeButton.addEventListener("click", () => {
    const next = document.documentElement.dataset.theme === "light" ? "dark" : "light";
    document.documentElement.dataset.theme = next;
    try { localStorage.setItem("redkale-docs-theme", next); } catch (_) { /* Storage is optional. */ }
    updateThemeButton();
  });
  updateThemeButton();

  function setMenu(open, returnFocus = false) {
    open = mobile.matches && open;
    document.body.classList.toggle("menu-open", open);
    menuButton.setAttribute("aria-expanded", String(open));
    menuButton.setAttribute("aria-label", open ? "关闭导航菜单" : "打开导航菜单");
    sidebar.inert = mobile.matches && !open;
    document.querySelector(".docs-layout").inert = open;
    if (open) sidebar.querySelector("a[aria-current='page']")?.focus();
    else if (returnFocus) menuButton.focus();
  }
  menuButton.addEventListener("click", () => setMenu(!document.body.classList.contains("menu-open"), true));
  document.querySelector(".menu-backdrop").addEventListener("click", () => setMenu(false, true));
  sidebar.addEventListener("click", event => { if (event.target.closest("a")) setMenu(false); });
  mobile.addEventListener("change", () => setMenu(false));
  setMenu(false);

  // Remove legacy paragraph indentation, preserving whitespace in code examples.
  const walker = document.createTreeWalker(article, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    const node = walker.currentNode;
    if (!node.parentElement.closest("pre, code")) {
      node.nodeValue = node.nodeValue.replace(/^[\t\r\n ]*\u00a0[\u00a0\t\r\n ]*/, "");
    }
  }
  const headings = Array.from(article.querySelectorAll("h2, h3, h4")).filter(heading => heading.textContent.trim());
  const anchors = [];
  headings.forEach((heading, index) => {
    heading.setAttribute("aria-level", heading.tagName === "H4" ? "3" : "2");
    const oldAnchor = heading.querySelector("a[id]");
    if (!heading.id) {
      heading.id = oldAnchor?.id || `section-${index + 1}`;
      oldAnchor?.removeAttribute("id");
    }
    heading.querySelectorAll("a[aria-hidden]").forEach(anchor => {
      if (anchor.textContent.trim()) anchor.removeAttribute("aria-hidden");
    });
    if (oldAnchor?.getAttribute("href") === "#") oldAnchor.href = `#${heading.id}`;
    const title = heading.textContent.trim();
    const anchor = document.createElement("a");
    anchor.className = "heading-anchor";
    anchor.href = `#${heading.id}`;
    anchor.textContent = "#";
    anchor.setAttribute("aria-label", `${title}的直接链接`);
    heading.append(anchor);
    anchors.push({ id: heading.id, title, nested: heading.tagName === "H4" });
  });
  for (const list of document.querySelectorAll(".toc-links")) {
    anchors.forEach(({ id, title, nested }) => {
      const item = document.createElement("li");
      const link = document.createElement("a");
      link.href = `#${id}`;
      link.textContent = title;
      if (nested) link.className = "toc-nested";
      item.append(link);
      list.append(item);
    });
  }
  if (!anchors.length) {
    document.querySelector(".docs-toc").hidden = true;
    document.querySelector(".mobile-toc").hidden = true;
  }
  let ticking = false;
  function updateTOC() {
    const threshold = parseInt(getComputedStyle(document.documentElement).getPropertyValue("--header-height")) + 70;
    let current = headings[0];
    for (const heading of headings) {
      if (heading.getBoundingClientRect().top <= threshold) current = heading;
      else break;
    }
    document.querySelectorAll(".toc-links a").forEach(link => {
      if (link.getAttribute("href") === `#${current?.id}`) link.setAttribute("aria-current", "location");
      else link.removeAttribute("aria-current");
    });
    ticking = false;
  }
  window.addEventListener("scroll", () => {
    if (!ticking) { ticking = true; requestAnimationFrame(updateTOC); }
  }, { passive: true });
  updateTOC();
  if (location.hash) {
    try { document.getElementById(decodeURIComponent(location.hash.slice(1)))?.scrollIntoView(); }
    catch (_) { /* Ignore malformed fragments. */ }
  }
  article.querySelectorAll("a[target='_blank']").forEach(link => {
    const href = link.getAttribute("href");
    if (href && !/^(https?:)?\/\//.test(href)) link.removeAttribute("target");
    else link.rel = "noopener noreferrer";
  });
  article.querySelectorAll("img").forEach(image => { image.loading = "lazy"; image.decoding = "async"; });
  article.querySelectorAll("[style]").forEach(element => {
    if (element.style.color && !element.closest("pre, code")) {
      element.style.removeProperty("color");
      element.classList.add("legacy-emphasis");
    }
  });

  article.querySelectorAll(".highlight").forEach(block => {
    const pre = block.querySelector("pre");
    if (!pre) return;
    const code = pre.textContent;
    const toolbar = document.createElement("div");
    toolbar.className = "code-toolbar";
    const language = document.createElement("span");
    language.textContent = /^\s*</.test(code) ? "XML" : /\b(public|class|package|import)\b/.test(code) ? "Java" : "代码";
    const button = document.createElement("button");
    button.type = "button";
    button.className = "copy-code";
    button.textContent = "复制代码";
    button.setAttribute("aria-label", "复制代码");
    button.setAttribute("aria-live", "polite");
    button.addEventListener("click", async () => {
      try {
        if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(code);
        else {
          const textarea = document.createElement("textarea");
          textarea.value = code;
          textarea.style.cssText = "position:fixed;left:-9999px;top:0";
          document.body.append(textarea);
          textarea.select();
          try { if (!document.execCommand("copy")) throw new Error("Copy unavailable"); }
          finally { textarea.remove(); button.focus(); }
        }
        button.textContent = "已复制";
      } catch (_) { button.textContent = "复制失败，请手动选择"; }
      setTimeout(() => { button.textContent = "复制代码"; }, 1800);
    });
    toolbar.append(language, button);
    block.prepend(toolbar);
  });

  const dialog = document.querySelector(".search-dialog");
  const input = dialog.querySelector("input");
  const results = dialog.querySelector(".search-results");
  let indexPromise;
  let queryVersion = 0;
  let previousFocus;
  function message(text) {
    const paragraph = document.createElement("p");
    paragraph.className = "search-message";
    paragraph.textContent = text;
    results.replaceChildren(paragraph);
  }
  function loadIndex() {
    if (!indexPromise) indexPromise = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = "scripts/search-index.js";
      script.onload = () => resolve(window.REDKALE_SEARCH_INDEX || []);
      script.onerror = () => { indexPromise = undefined; script.remove(); reject(new Error("Search index unavailable")); };
      document.head.append(script);
    });
    return indexPromise;
  }
  async function search() {
    const version = ++queryVersion;
    const query = input.value.trim().toLocaleLowerCase();
    message(query ? "正在搜索…" : "输入关键词，搜索组件、教程和代码示例。");
    if (!query) return;
    let records;
    try { records = await loadIndex(); }
    catch (_) { if (version === queryVersion) message("搜索暂时不可用，请使用左侧导航查找文档。"); return; }
    if (version !== queryVersion) return;
    const terms = query.split(/\s+/);
    const matches = [];
    for (const record of records) {
      const combined = `${record.page} ${record.title} ${record.text}`.toLocaleLowerCase();
      if (!terms.every(term => combined.includes(term))) continue;
      const title = `${record.page} ${record.title}`.toLocaleLowerCase();
      const score = terms.reduce((total, term) => total + (title.includes(term) ? 10 : 1), 0);
      matches.push({ record, score });
    }
    matches.sort((a, b) => b.score - a.score);
    if (!matches.length) { message(`没有找到“${input.value.trim()}”相关文档，请尝试其他关键词。`); return; }
    results.replaceChildren();
    for (const { record } of matches.slice(0, 20)) {
      const link = document.createElement("a");
      link.className = "search-result";
      link.href = record.href;
      const category = document.createElement("small");
      category.textContent = record.page === record.title ? "文档概览" : record.page;
      const title = document.createElement("strong");
      title.textContent = record.title;
      const excerpt = document.createElement("p");
      const position = record.text.toLocaleLowerCase().indexOf(terms[0]);
      const start = Math.max(0, position - 30);
      excerpt.textContent = `${start ? "…" : ""}${record.text.slice(start, start + 115)}${record.text.length > start + 115 ? "…" : ""}`;
      link.append(category, title, excerpt);
      results.append(link);
    }
  }
  function openSearch() {
    if (document.body.classList.contains("menu-open")) setMenu(false);
    if (!dialog.open) {
      previousFocus = document.activeElement;
      dialog.showModal();
    }
    input.focus();
    search();
  }
  document.querySelector(".search-trigger").addEventListener("click", openSearch);
  dialog.querySelector(".search-close").addEventListener("click", () => dialog.close());
  dialog.addEventListener("click", event => {
    if (event.target === dialog) {
      const rect = dialog.getBoundingClientRect();
      if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
    }
  });
  dialog.addEventListener("close", () => previousFocus?.focus());
  input.addEventListener("input", search);
  input.addEventListener("keydown", event => {
    if (event.key === "ArrowDown") { event.preventDefault(); results.querySelector("a")?.focus(); }
    else if (event.key === "Enter") results.querySelector("a")?.click();
  });
  results.addEventListener("keydown", event => {
    const links = Array.from(results.querySelectorAll("a"));
    const index = links.indexOf(document.activeElement);
    if (event.key === "ArrowDown") { event.preventDefault(); links[Math.min(index + 1, links.length - 1)]?.focus(); }
    else if (event.key === "ArrowUp") { event.preventDefault(); if (index <= 0) input.focus(); else links[index - 1]?.focus(); }
  });
  document.addEventListener("keydown", event => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") { event.preventDefault(); openSearch(); }
    else if (event.key === "Escape" && document.body.classList.contains("menu-open")) setMenu(false, true);
  });
})();
