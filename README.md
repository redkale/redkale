# Redkale 官网文档

这是可直接部署到 GitHub Pages 的静态站点。文档采用固定顶栏、分组侧边栏、正文和页内目录布局，支持深浅主题、全文搜索、代码复制及移动端导航。

## 本地预览

在仓库目录运行：

```sh
python -m http.server 8080 --bind 127.0.0.1
```

打开 `http://127.0.0.1:8080/`。无需安装 Node.js 依赖或运行前端打包。

## 修改文档

正文位于各 HTML 文件的 `docs-content:start` 和 `docs-content:end` 注释之间。编辑正文后运行：

```sh
python scripts/build_docs.py
```

脚本使用 Python 3 标准库，同步 28 个文档页面的导航、标题、描述及搜索索引。导航分组和页面元数据统一维护在 `scripts/build_docs.py` 中。

运行 `python scripts/check_docs.py` 可检查导航链接、正文保留情况和重复生成的一致性。

- `stylesheets/docs.css`：排版、主题和响应式布局。
- `scripts/docs.js`：导航、目录、代码复制与搜索交互。
- `scripts/theme.js`：在页面绘制前读取主题偏好。
- `scripts/search-index.js`：生成的搜索索引，首次搜索时加载。
- `javadoc/`：独立生成的 Java API 文档。
- `doc/`：原有下载文档。

页面路径与已有正文锚点保持兼容。更新历史教程的接口和代码需要单独校对当前 Redkale 版本。
