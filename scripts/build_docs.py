"""Refresh the shared static documentation shell and local search index.

Only Python's standard library is required. Existing article markup is kept
between docs-content markers; Javadoc and downloaded documents are untouched.
"""

from html import escape
from html.parser import HTMLParser
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent.parent
PAGES = {
    "index.html": ("简介", "Redkale 文档", "开始使用", "Redkale 是一个以组件为核心的 Java 微服务开源框架。通过统一的服务、数据和网络接口，构建从单体应用到分布式服务的 Java 应用。"),
    "redkale.html": ("快速入门", "Redkale 快速入门", "开始使用", "了解项目结构、应用启动、依赖注入与配置方式，从这里开始使用 Redkale。"),
    "code.html": ("源码与下载", "源码与下载", "开始使用", "获取 Redkale 源码、查看在线 API，了解开源项目的参与方式。"),
    "convert.html": ("Convert · 序列化", "Convert 组件", "核心组件", "JSON 与 Protobuf 序列化、泛型数据转换和自定义编解码器。Convert 可以作为独立组件使用。"),
    "service.html": ("Service · 服务", "Service 组件", "核心组件", "组织业务逻辑，通过配置在本地服务与远程服务之间切换，保持一致的调用接口。"),
    "source.html": ("Source · 数据与缓存", "Source 组件", "核心组件", "使用 DataSource 和 CacheSource 访问数据库与缓存，支持同步、异步以及分库分表等场景。"),
    "net.html": ("Net · 网络服务", "Net 组件", "核心组件", "了解 HTTP、WebSocket 和 SNCP 服务的配置、请求处理与异步通信接口。"),
    "watch.html": ("Watch · 运行管理", "Watch 组件", "核心组件", "通过 Watch 接口查看运行状态、管理服务及动态调整应用配置。"),
    "plugins.html": ("Redkale 插件", "Redkale 插件", "生态与资源", "通过插件扩展数据存储、支付、消息通信以及其他第三方服务。"),
    "articles.html": ("教程与技术文章", "教程与技术文章", "生态与资源", "从应用开发示例到框架内部原理，阅读 Redkale 的教程与技术文章。"),
    "course01_hello.html": ("01 · Hello World", "Hello World", "开发教程", "创建第一个 Redkale 应用，了解项目目录、启动配置和简单的 HTTP 接口。"),
    "course01_helloidea.html": ("01 · IntelliJ IDEA 与 Maven", "Hello World · IntelliJ IDEA", "开发教程", "使用 IntelliJ IDEA 和 Maven 创建并运行第一个 Redkale 项目。"),
    "course02_dbconn.html": ("02 · 数据库连接", "DataSource 数据库连接", "开发教程", "了解 DataSource 连接配置与数据访问示例。"),
    "course02_rest.html": ("02 · REST 敏捷开发", "REST 敏捷开发", "开发教程", "通过服务注解生成 REST 接口，减少接入层的重复代码。"),
    "course03_userauth.html": ("03 · 用户鉴权", "用户鉴权", "开发教程", "了解 Redkale 应用中的用户身份校验与接口权限处理。"),
    "course04_cachedb.html": ("04 · 缓存数据源", "CacheSource 缓存数据源", "开发教程", "使用统一的缓存接口组织应用中的缓存数据访问。"),
    "course05_dbaction.html": ("05 · 数据增删改查", "DataSource 增删改查", "开发教程", "了解实体对象、条件查询和常见数据库操作。"),
    "course06_dbdis.html": ("06 · 分库分表", "DataSource 分库分表", "开发教程", "了解数据源的分库分表机制与应用示例。"),
    "course07_convert.html": ("07 · 数据序列化", "Convert 数据序列化", "开发教程", "在应用中使用 Redkale 的数据序列化与反序列化组件。"),
    "course08_httpservlet.html": ("08 · HTTP 接口", "HttpServlet HTTP 接口", "开发教程", "了解 HTTP 请求、响应与 Servlet 接口的开发方式。"),
    "course09_fileup.html": ("09 · 文件上传下载", "文件上传与下载", "开发教程", "了解 multipart 文件上传及 HTTP 文件响应的使用方式。"),
    "course10_websocket.html": ("10 · WebSocket", "WebSocket 实时通信", "开发教程", "了解 WebSocket 连接、消息处理和实时通信服务。"),
    "course11_sncp.html": ("11 · SNCP 分布式部署", "SNCP 分布式部署", "开发教程", "了解通过 SNCP 协议实现服务间通信与分布式部署的方式。"),
    "article_parents.html": ("双亲委托模型", "双亲委托模型", "技术文章", "Redkale 技术详解：类加载与框架组件中的双亲委托模型。"),
    "article_creator.html": ("Creator 对象构建", "Creator 对象构建", "技术文章", "Redkale 技术详解：对象构建接口与动态代码生成的设计。"),
    "article_convert.html": ("Convert 高性能序列化", "Convert 高性能序列化", "技术文章", "Redkale 技术详解：序列化组件的设计思路与实现原理。"),
    "article_source.html": ("DataSource 数据访问", "DataSource 数据访问", "技术文章", "Redkale 技术详解：简洁的数据访问接口与数据库操作。"),
    "article_regain.html": ("重新认识 Java", "Redkale，让你重新认识 Java", "技术文章", "阅读 Redkale 框架的设计思路，以及组件化应用开发的实践。"),
}
GROUPS = [
    ("开始使用", ["index.html", "redkale.html", "course01_helloidea.html", "code.html"]),
    ("核心组件", ["convert.html", "service.html", "source.html", "net.html", "watch.html"]),
    ("开发教程", [name for name in PAGES if name.startswith("course") and name != "course01_helloidea.html"]),
    ("生态与资源", ["plugins.html", "articles.html", "javadoc/index.html"]),
    ("技术文章", [name for name in PAGES if name.startswith("article_")]),
]


def icon(paths, css=""):
    return f'<svg class="{css}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">{paths}</svg>'


EXTERNAL = icon('<path d="M14 3h7v7M21 3l-11 11M10 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5"/>')
HOME = icon('<path d="m3 10 9-7 9 7M5 9v12h5v-7h4v7h5V9"/>')
SEARCH = icon('<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>')
ARROW = icon('<path d="M5 12h14m-5-5 5 5-5 5"/>')
SUN = icon('<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>', "theme-sun")
MOON = icon('<path d="M21 13a9 9 0 1 1-10-10 7 7 0 0 0 10 10Z"/>', "theme-moon")
MENU = icon('<path d="M4 6h16M4 12h16M4 18h16"/>')
CLOSE = icon('<path d="m6 6 12 12M6 18 18 6"/>')


def shell(current):
    groups = []
    for title, names in GROUPS:
        opened = title in ("开始使用", "核心组件", "生态与资源") or current in names
        links = []
        for name in names:
            label = "Javadoc API" if name.startswith("javadoc/") else PAGES[name][0]
            active = ' aria-current="page"' if name == current else ""
            links.append(f'<li><a class="nav-link" href="{name}"{active}>{escape(label)}{EXTERNAL if name.startswith("javadoc/") else ""}</a></li>')
        groups.append(f'<details class="nav-group"{" open" if opened else ""}><summary>{title}</summary><ul>{"".join(links)}</ul></details>')
    return f'''<!-- docs-shell:start -->
<a class="skip-link" href="#main-content">跳到主要内容</a>
<header class="docs-header">
  <div class="header-start">
    <button class="icon-button menu-toggle" type="button" aria-label="打开导航菜单" aria-controls="docs-sidebar" aria-expanded="false">{MENU}</button>
    <a class="docs-brand" href="index.html" aria-label="Redkale 文档首页"><img src="logo_96.png" alt="" width="35" height="35"><span>Redkale <span>Docs</span></span></a>
  </div>
  <nav class="header-actions" aria-label="主导航">
    <button class="search-trigger" type="button" aria-label="搜索文档">{SEARCH}<span>搜索文档…</span><kbd>Ctrl K</kbd></button>
    <span class="header-language" lang="zh-CN">简体中文</span>
    <a class="header-link" href="https://github.com/redkale/redkale" target="_blank" rel="noopener noreferrer">GitHub {EXTERNAL}</a>
    <a class="header-link" href="https://gitee.com/redkale/redkale" target="_blank" rel="noopener noreferrer">Gitee {EXTERNAL}</a>
    <button class="icon-button theme-toggle" type="button" aria-label="切换到浅色主题">{MOON}{SUN}</button>
  </nav>
</header>
<aside class="docs-sidebar" id="docs-sidebar" aria-label="文档导航">
  <p class="sidebar-label">REDKALE DOCUMENTATION</p>
  <nav aria-label="文档章节">{"".join(groups)}</nav>
  <div class="sidebar-footer">Java 微服务开源框架<br><a href="https://redkale.org">redkale.org</a> · Apache License 2.0</div>
</aside>
<button class="menu-backdrop" type="button" tabindex="-1" aria-label="关闭导航菜单"></button>
<!-- docs-shell:end -->'''


def intro(name):
    label, title, group, description = PAGES[name]
    note = ""
    if name.startswith(("course", "article_")):
        note = '<div class="doc-note">历史教程与技术文章保留了原版示例。开发新项目时，请对照当前版本的 Javadoc API 使用。</div>'
    quick = ""
    if name == "index.html":
        cards = [("redkale.html", "快速入门", "项目结构、启动与配置"), ("course01_helloidea.html", "创建第一个应用", "IntelliJ IDEA 与 Maven"), ("net.html", "网络与服务", "HTTP、WebSocket 与 SNCP"), ("javadoc/index.html", "API 参考", "查看 Java 接口与方法")]
        quick = '<div class="quick-links">' + "".join(f'<a class="quick-link" href="{href}"><strong>{title}</strong><span>{text}</span>{ARROW}</a>' for href, title, text in cards) + '</div>'
    return f'''<!-- docs-intro:start -->
<nav class="breadcrumbs" aria-label="页面路径"><a href="index.html" aria-label="文档首页">{HOME}</a><span aria-hidden="true">›</span><span>{group}</span><span aria-hidden="true">›</span><span class="current">{escape(label)}</span></nav>
<header class="doc-intro"><h1>{escape(title)}</h1><p class="doc-description">{escape(description)}</p></header>
<details class="mobile-toc"><summary>本页目录</summary><ul class="toc-links"></ul></details>
{note}{quick}
<!-- docs-intro:end -->'''


def pagination(name):
    group = next(names for _, names in GROUPS if name in names)
    position = group.index(name)
    previous = group[position - 1] if position else None
    following = group[position + 1] if position + 1 < len(group) else None
    links = []
    if previous:
        links.append(f'<a href="{previous}"><span>上一页</span>‹ {escape(PAGES[previous][0])}</a>')
    if following:
        label = "Javadoc API" if following.startswith("javadoc/") else PAGES[following][0]
        links.append(f'<a class="next" href="{following}"><span>下一页</span>{escape(label)} ›</a>')
    return '<nav class="doc-pagination" aria-label="文档翻页">' + "".join(links) + '</nav>'


SEARCH_DIALOG = f'''<dialog class="search-dialog" aria-label="搜索文档">
  <div class="search-field">{SEARCH}<input type="search" placeholder="搜索组件、教程、API…" aria-label="搜索关键词" autocomplete="off"><button class="icon-button search-close" type="button" aria-label="关闭搜索">{CLOSE}</button></div>
  <div class="search-results" aria-live="polite"></div>
  <div class="search-help"><span><kbd>↑ ↓</kbd> 选择结果</span><span><kbd>Enter</kbd> 打开文档</span><span><kbd>Esc</kbd> 关闭</span></div>
</dialog>'''


class SearchParser(HTMLParser):
    def __init__(self, page, name):
        super().__init__(convert_charrefs=True)
        self.page = page
        self.name = name
        self.records = []
        self.current = {"page": page, "title": page, "href": name, "text": ""}
        self.heading = None
        self.heading_count = 0
        self.ignored = 0

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if tag in ("script", "style"):
            self.ignored += 1
        if tag in ("h2", "h3", "h4"):
            self.heading = {"title": "", "id": attributes.get("id", "")}
        elif self.heading is not None and tag == "a" and attributes.get("id"):
            self.heading["id"] = attributes["id"]
        if tag in ("p", "br", "li", "div", "pre", "tr"):
            self.current["text"] += " "

    def handle_endtag(self, tag):
        if tag in ("script", "style"):
            self.ignored = max(0, self.ignored - 1)
        if tag in ("h2", "h3", "h4") and self.heading is not None:
            title = self.heading["title"].strip()
            if title:
                self.records.append(self.current)
                self.heading_count += 1
                anchor = self.heading["id"] or f"section-{self.heading_count}"
                self.current = {"page": self.page, "title": title, "href": f"{self.name}#{anchor}", "text": ""}
            self.heading = None

    def handle_data(self, text):
        if self.ignored:
            return
        if self.heading is not None:
            self.heading["title"] += text
        else:
            self.current["text"] += text

    def finish(self):
        self.records.append(self.current)
        for record in self.records:
            record["text"] = re.sub(r"\s+", " ", record["text"]).strip()
        return self.records


def build():
    records = []
    for name, (_, title, _, description) in PAGES.items():
        path = ROOT / name
        source = path.read_text(encoding="utf-8-sig")
        marked = re.search(r"<!-- docs-content:start -->(.*?)<!-- docs-content:end -->", source, re.S)
        if marked:
            content = marked.group(1)
            before = source[:source.index("<body")]
            analytics = re.search(r"(<script>\s*var _hmt.*?</script>)", source, re.S)
        else:
            original = re.search(r'<section class="main-content">(.*?)</section>', source, re.S)
            if not original:
                raise ValueError(f"Missing article content: {name}")
            content = original.group(1)
            before = source[:source.index("<body")]
            analytics = re.search(r"(<script>\s*var _hmt.*?</script>)", source[original.end():], re.S)
        before = before.replace('lang="en-us"', 'lang="zh-CN" data-theme="dark"')
        page_title = title if name == "index.html" else f"{title} | Redkale 文档"
        before = re.sub(r"<title>.*?</title>", f"<title>{escape(page_title)}</title>", before)
        before = re.sub(r'<meta name="description"[^>]*>', f'<meta name="description" content="{escape(description, quote=True)}">', before)
        before = before.replace("stylesheets/stylesheet.css", "stylesheets/docs.css")
        if 'src="scripts/theme.js"' not in before:
            before = before.replace("</head>", '<link rel="icon" href="favicon.ico">\n        <script src="scripts/theme.js"></script>\n    </head>')
        output = before + f'''<body>
{shell(name)}
<main id="main-content" class="docs-layout" tabindex="-1">
  <article class="main-content">
{intro(name)}
<!-- docs-content:start -->{content}<!-- docs-content:end -->
{pagination(name)}
  </article>
  <aside class="docs-toc" aria-label="本页目录"><p class="toc-title">本页目录</p><ul class="toc-links"></ul></aside>
</main>
{SEARCH_DIALOG}
<script src="scripts/docs.js" defer></script>
{analytics.group(1) if analytics else ""}
</body>
</html>
'''
        path.write_text(output, encoding="utf-8", newline="\n")
        parser = SearchParser(title, name)
        parser.feed(content)
        page_records = parser.finish()
        if not page_records[0]["text"]:
            page_records[0]["text"] = description
        records.extend(page_records)
    index = "// Generated by scripts/build_docs.py.\nwindow.REDKALE_SEARCH_INDEX = " + json.dumps(records, ensure_ascii=False, separators=(",", ":")) + ";\n"
    (ROOT / "scripts/search-index.js").write_text(index, encoding="utf-8", newline="\n")
    print(f"Updated {len(PAGES)} documentation pages and {len(records)} search sections.")


if __name__ == "__main__":
    build()
