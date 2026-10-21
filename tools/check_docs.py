"""Portable local documentation links, heading fragments, pictures and reference hashes."""
from pathlib import Path
import hashlib
import json
import re
import struct
import sys
from urllib.parse import unquote, urlparse

ROOT = Path(__file__).resolve().parents[1]
errors = []
links = fragments = pictures = 0

def anchors(path):
    content = path.read_text(encoding="utf-8-sig")
    found, counts = set(), {}
    for heading in re.findall(r"^#{1,6}\s+(.+)$", content, re.M):
        slug = re.sub(r"[^\w\- ]", "", heading.strip().lower()).replace(" ", "-")
        count = counts.get(slug, 0); counts[slug] = count + 1
        found.add(slug if not count else f"{slug}-{count}")
    found.update(re.findall(r'<a\s+id="([^"]+)"', content))
    return found

for document in [ROOT / "README.md", *sorted((ROOT / "docs").rglob("*.md"))]:
    content = document.read_text(encoding="utf-8-sig")
    for reference in re.findall(r"\[[^\]]*\]\(([^)]+)\)", content) + re.findall(r'<img[^>]+src="([^"]+)"', content):
        reference = reference.strip("<>")
        parsed = urlparse(reference)
        if parsed.scheme or reference.startswith("//"):
            continue
        target = (document.parent / unquote(parsed.path)).resolve() if parsed.path else document
        links += 1
        if not target.is_file():
            errors.append(f"{document.relative_to(ROOT)}: missing {reference}")
        elif parsed.fragment:
            fragments += 1
            if target.suffix != ".md" or unquote(parsed.fragment) not in anchors(target):
                errors.append(f"{document.relative_to(ROOT)}: missing fragment {reference}")
    for line_no, line in enumerate(content.splitlines(), 1):
        if line.rstrip() != line:
            errors.append(f"{document.relative_to(ROOT)}:{line_no}: trailing whitespace")

for picture in [*(ROOT / "docs/assets").rglob("*.png"), *(ROOT / "android/app/src/androidTest/assets/goldens").glob("*.png")]:
    data = picture.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n" or min(struct.unpack(">II", data[16:24])) <= 0:
        errors.append(f"Invalid PNG: {picture.name}")
    pictures += 1

manifest = ROOT / "docs/assets/screens/reference-hashes.json"
for name, expected in json.loads(manifest.read_text()).items():
    actual = hashlib.sha256((manifest.parent / name).read_bytes()).hexdigest()
    if actual != expected:
        errors.append(f"Changed original concept image: {name}")

print(f"Checked {links} local references, {fragments} fragments, {pictures} PNGs and 5 original-image hashes")
if errors:
    print("\n".join(errors)); sys.exit(1)
print("Documentation checks passed")
