#!/usr/bin/env python3
"""Build a self-contained HTML review pack from the four architecture docs.

Mermaid source blocks are stripped (the rendered PNGs under diagrams/ are the deliverable)
and every image is inlined as a base64 data URI so the HTML can be emailed or attached.

Usage: python3 docs/architecture/build-review-pack.py <output.html>
"""
import base64
import mimetypes
import re
import sys
from pathlib import Path

import markdown

HERE = Path(__file__).resolve().parent
DOCS = ["current-state.md", "target-state.md", "open-questions.md", "migration-plan.md"]
TITLES = {
    "current-state.md": "1. Current State",
    "target-state.md": "2. Target State",
    "open-questions.md": "3. Open Questions",
    "migration-plan.md": "4. Migration Plan",
}

CSS = """
@page { size: A3 landscape; margin: 12mm; }
body { font-family: -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; color: #111;
       line-height: 1.45; font-size: 11pt; max-width: 100%; margin: 0; padding: 0 8px; }
h1 { font-size: 22pt; border-bottom: 2px solid #111; padding-bottom: 6px; margin-top: 28px; }
h2 { font-size: 15pt; margin-top: 22px; border-bottom: 1px solid #ccc; padding-bottom: 3px; }
h3 { font-size: 12.5pt; margin-top: 16px; }
table { border-collapse: collapse; width: 100%; margin: 10px 0; font-size: 9pt;
        table-layout: auto; word-wrap: break-word; }
th, td { border: 1px solid #bbb; padding: 5px 7px; text-align: left; vertical-align: top; }
th { background: #f0f0f0; }
tr { page-break-inside: avoid; }
code { background: #f4f4f4; padding: 1px 3px; font-size: 8.5pt; word-break: break-all; }
img { max-width: 100%; height: auto; page-break-inside: avoid; }
blockquote { border-left: 4px solid #b58105; background: #fff8e6; margin: 12px 0;
             padding: 8px 14px; }
.doc { page-break-before: always; }
.doc:first-of-type { page-break-before: avoid; }
hr { border: none; border-top: 1px solid #ddd; margin: 18px 0; }
.cover { text-align: left; padding: 10px 0 20px; }
.cover .meta { color: #555; font-size: 10pt; }
"""

MERMAID_DETAILS = re.compile(
    r"<details>\s*\n<summary>Mermaid source.*?</summary>.*?</details>\s*", re.S
)


def inline_images(html: str) -> str:
    def repl(match: re.Match) -> str:
        src = match.group(1)
        path = (HERE / src).resolve()
        if not path.exists():
            raise SystemExit(f"missing image: {path}")
        mime = mimetypes.guess_type(str(path))[0] or "image/png"
        b64 = base64.b64encode(path.read_bytes()).decode()
        return f'src="data:{mime};base64,{b64}"'

    return re.sub(r'src="([^"]+)"', repl, html)


def main() -> None:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "fineract-aws-migration-review-pack.html"
    md = markdown.Markdown(extensions=["tables", "fenced_code", "toc", "attr_list"])
    parts = [
        '<div class="cover"><h1>Fineract on AWS — Migration Architecture Review Pack</h1>'
        '<p class="meta">Repository: COG-GTM/fineract-Core-Banking &middot; '
        "Contents: current state, target state, open questions, migration plan.<br>"
        "The approved AWS service list and the platform migration guardrails were not supplied; "
        "both are documented as labelled assumptions and raised as open questions OQ-P1 and OQ-P2."
        "</p></div>"
    ]
    for name in DOCS:
        text = MERMAID_DETAILS.sub("", (HERE / name).read_text())
        md.reset()
        parts.append(f'<div class="doc">{md.convert(text)}</div>')
    html = (
        "<!doctype html><html><head><meta charset='utf-8'>"
        "<title>Fineract on AWS — Migration Architecture Review Pack</title>"
        f"<style>{CSS}</style></head><body>{''.join(parts)}</body></html>"
    )
    out.write_text(inline_images(html))
    print(f"wrote {out} ({out.stat().st_size / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
