#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ZH = json.loads((ROOT / "src/main/resources/assets/plot/lang/zh_cn.json").read_text(encoding="utf-8"))
EN = json.loads((ROOT / "src/main/resources/assets/plot/lang/en_us.json").read_text(encoding="utf-8"))
blob = "\n".join(
    p.read_text(encoding="utf-8", errors="ignore") for p in (ROOT / "src").rglob("*.java")
)
lits = set(
    re.findall(r'"((?:plugin\.road|hint\.plot\.road|preset\.road)[^"]+)"', blob)
)
missing_zh = sorted(k for k in lits if k not in ZH)
missing_en = sorted(k for k in lits if k not in EN)
print(f"literals={len(lits)} missing_zh={len(missing_zh)} missing_en={len(missing_en)}")
for k in missing_zh:
    print("ZH", k)
for k in missing_en:
    print("EN", k)
