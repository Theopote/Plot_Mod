#!/usr/bin/env python3
"""Scan unused road-related i18n keys, accounting for dynamic concatenation."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ZH_PATH = ROOT / "src/main/resources/assets/plot/lang/zh_cn.json"
EN_PATH = ROOT / "src/main/resources/assets/plot/lang/en_us.json"
OUT_PATH = ROOT / "tools/.tmp_unused_road_i18n.txt"

PREFIXES = ("plugin.road.", "hint.plot.road.", "preset.road.")
STR_LIT_RE = re.compile(r'"((?:plugin\.road|hint\.plot\.road|preset\.road)[^"]*)"')
CONCAT_RE = re.compile(r'"((?:plugin\.road|hint\.plot\.road|preset\.road)[^"]*)"\s*\+')


def main() -> None:
    zh = json.loads(ZH_PATH.read_text(encoding="utf-8"))
    road_keys = sorted(k for k in zh if k.startswith(PREFIXES))

    java_files = list((ROOT / "src/main/java").rglob("*.java"))
    java_files += list((ROOT / "src/test").rglob("*.java"))

    literal_keys: set[str] = set()
    concat_prefixes: set[str] = set()
    blobs: list[str] = []
    for path in java_files:
        text = path.read_text(encoding="utf-8", errors="ignore")
        blobs.append(text)
        literal_keys.update(STR_LIT_RE.findall(text))
        concat_prefixes.update(CONCAT_RE.findall(text))
    blob = "\n".join(blobs)

    used: set[str] = set()
    for key in road_keys:
        if key in literal_keys or key in blob:
            used.add(key)
            continue
        if any(key.startswith(prefix) and key != prefix for prefix in concat_prefixes):
            used.add(key)

    unused = [k for k in road_keys if k not in used]
    OUT_PATH.write_text("\n".join(unused) + "\n", encoding="utf-8")

    print(f"road keys: {len(road_keys)}")
    print(f"literal keys: {len(literal_keys)}")
    print(f"concat prefixes: {len(concat_prefixes)}")
    for prefix in sorted(concat_prefixes):
        print(f"  CONCAT {prefix}")
    print(f"unused: {len(unused)}")
    print(f"wrote {OUT_PATH}")

    # Also report keys present in zh but missing in en (and vice versa) among road keys
    en = json.loads(EN_PATH.read_text(encoding="utf-8"))
    only_zh = sorted(k for k in road_keys if k not in en)
    only_en = sorted(k for k in en if k.startswith(PREFIXES) and k not in zh)
    print(f"only_zh: {len(only_zh)} only_en: {len(only_en)}")


if __name__ == "__main__":
    main()
