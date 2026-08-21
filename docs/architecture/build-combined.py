#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#
"""Build the combined self-contained HTML review package from the four architecture docs.

Diagrams are inlined as base64 PNGs and the collapsed Mermaid source blocks are stripped,
so the output is a single file suitable for printing to PDF.
"""
import base64
import pathlib
import re

import markdown

HERE = pathlib.Path(__file__).parent
DOCS = ["current-state.md", "target-state.md", "open-questions.md", "migration-plan.md"]
OUT = HERE / "fineract-aws-migration-architecture.html"

HTML_LICENSE = """<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements. See the NOTICE file
distributed with this work for additional information
regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the License for the
specific language governing permissions and limitations
under the License.
-->
"""

CSS = """
@page { size: A3 landscape; margin: 12mm; }
body { font-family: -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; font-size: 10.5pt;
       line-height: 1.45; color: #17202a; margin: 0 auto; max-width: 1600px; padding: 24px; }
h1 { font-size: 20pt; border-bottom: 3px solid #17202a; padding-bottom: 6px; margin-top: 0; }
h2 { font-size: 14pt; margin-top: 22px; border-bottom: 1px solid #ccd3d9; padding-bottom: 3px; }
h3 { font-size: 12pt; margin-top: 16px; }
table { border-collapse: collapse; width: 100%; margin: 10px 0 18px; table-layout: auto; }
th, td { border: 1px solid #b8c2cc; padding: 5px 7px; text-align: left; vertical-align: top;
         font-size: 9pt; word-wrap: break-word; overflow-wrap: anywhere; }
th { background: #eef2f5; }
th:first-child, th:last-child, th:nth-last-child(2) { white-space: nowrap; }
tr { page-break-inside: avoid; }
code { font-family: "SFMono-Regular", Consolas, monospace; font-size: 8.6pt;
       background: #f2f4f7; padding: 0 2px; border-radius: 2px; overflow-wrap: anywhere; }
pre { background: #f2f4f7; padding: 10px; overflow-x: auto; font-size: 8.5pt; }
img { max-width: 100%; height: auto; display: block; margin: 12px auto 20px; }
blockquote { border-left: 4px solid #d1a000; background: #fffbe9; margin: 12px 0;
             padding: 8px 14px; }
.doc { page-break-before: always; }
.doc:first-of-type { page-break-before: avoid; }
.cover { page-break-after: always; }
.cover h1 { border: none; font-size: 26pt; }
.toc li { margin: 4px 0; }
"""


def inline_images(html: str) -> str:
    def repl(m: re.Match) -> str:
        src = m.group(1)
        path = HERE / src
        data = base64.b64encode(path.read_bytes()).decode()
        return f'src="data:image/png;base64,{data}"'

    return re.sub(r'src="([^"]+\.png)"', repl, html)


def main() -> None:
    md = markdown.Markdown(extensions=["tables", "fenced_code", "toc", "attr_list"])
    sections = []
    for name in DOCS:
        text = (HERE / name).read_text()
        # strip the collapsed Mermaid source blocks: rendered PNGs are the deliverable
        text = re.sub(r"<details>.*?</details>\n", "", text, flags=re.S)
        # internal cross-document links become in-page anchors
        text = re.sub(r"\]\((current-state|target-state|open-questions|migration-plan)\.md\)",
                      r"](#\1)", text)
        html = md.reset().convert(text)
        sections.append(f'<section class="doc" id="{name[:-3]}">\n{html}\n</section>')

    body = "\n".join(sections)
    cover = """
<section class="cover">
  <h1>Apache Fineract — Legacy-to-AWS Migration Architecture Package</h1>
  <p><strong>Repository:</strong> COG-GTM/fineract-Core-Banking</p>
  <p>Current state derived from the repository, an AWS target state assessed against the platform
     migration guardrails, the open questions that must be closed before design sign-off, and an
     ordered migration plan ending at production cutover.</p>
  <blockquote>The approved AWS service list and the platform guardrails were not supplied for this
     package. Both are <strong>assumed defaults, explicitly labelled as assumptions</strong> in the
     target-state document, and confirming them is open question OQ-P1 / OQ-P2.</blockquote>
  <ol class="toc">
    <li><a href="#current-state">Current State</a></li>
    <li><a href="#target-state">Target State (guardrails and guardrail compliance)</a></li>
    <li><a href="#open-questions">Open Questions</a></li>
    <li><a href="#migration-plan">Migration Plan</a></li>
  </ol>
</section>
"""
    doc = (f"{HTML_LICENSE}<!doctype html><html><head><meta charset='utf-8'>"
           f"<title>Fineract AWS Migration Architecture</title><style>{CSS}</style></head>"
           f"<body>{cover}{body}</body></html>")
    OUT.write_text(inline_images(doc))
    print(f"wrote {OUT} ({OUT.stat().st_size / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
