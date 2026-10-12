"""Check static navigation, original article preservation and build idempotence."""

from pathlib import Path
import hashlib
import re
import runpy
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
configuration = runpy.run_path(str(ROOT / "scripts/build_docs.py"))
pages = configuration["PAGES"]
checked_links = 0
for name in pages:
    source = (ROOT / name).read_text(encoding="utf-8")
    assert source.count('aria-current="page"') == 1, f"Active navigation: {name}"
    assert len(re.findall(r"<h1[ >]", source)) == 1, f"Page title: {name}"
    assert 'lang="zh-CN"' in source, f"Language: {name}"
    for required in ["stylesheets/docs.css", "scripts/docs.js", "scripts/theme.js"]:
        assert required in source, f"Missing shared asset {required}: {name}"
    shell = re.search(r"<!-- docs-shell:start -->(.*?)<!-- docs-shell:end -->", source, re.S).group(1)
    for target in re.findall(r'href="([^"]+)"', shell):
        if target.startswith(("#", "http:" , "https:")):
            continue
        assert (ROOT / target).exists(), f"Broken navigation {target}: {name}"
        checked_links += 1
    original = subprocess.run(["git", "show", f"HEAD:{name}"], cwd=ROOT, check=True, capture_output=True).stdout.decode("utf-8-sig")
    legacy = re.search(r'<section class="main-content">(.*?)</section>', original, re.S)
    if legacy:
        content = re.search(r"<!-- docs-content:start -->(.*?)<!-- docs-content:end -->", source, re.S).group(1)
        assert content.replace("\r\n", "\n") == legacy.group(1).replace("\r\n", "\n"), f"Article content changed: {name}"
files = [ROOT / name for name in pages] + [ROOT / "scripts/search-index.js"]
before = {file: hashlib.sha256(file.read_bytes()).digest() for file in files}
subprocess.run([sys.executable, str(ROOT / "scripts/build_docs.py")], cwd=ROOT, check=True)
assert all(hashlib.sha256(file.read_bytes()).digest() == before[file] for file in files), "Build is not idempotent"
print(f"PASS: {len(pages)} pages, {checked_links} navigation links, preserved article markup, idempotent build.")
