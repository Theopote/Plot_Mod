#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ZH = json.loads((ROOT / "src/main/resources/assets/plot/lang/zh_cn.json").read_text(encoding="utf-8"))
blob = "\n".join(p.read_text(encoding="utf-8", errors="ignore") for p in (ROOT / "src").rglob("*.java"))
keys = sorted(k for k in ZH if k.startswith("status.plot.road.") or k.startswith("hint.plot.road."))
concat = set(re.findall(r'"((?:status\.plot\.road|hint\.plot\.road)[^"]*)"\s*\+', blob))
unused = []
for key in keys:
    if key in blob:
        continue
    if any(key.startswith(p) and key != p for p in concat):
        continue
    unused.append(key)
print("hint/status road unused", len(unused))
for k in unused:
    print(k)
