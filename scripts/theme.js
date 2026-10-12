// Apply the saved theme before painting the page.
try {
  const theme = localStorage.getItem("redkale-docs-theme");
  if (theme === "light" || theme === "dark") document.documentElement.dataset.theme = theme;
} catch (_) {
  // Private browsing and local files may disable storage.
}
