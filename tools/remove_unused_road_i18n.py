#!/usr/bin/env python3
"""Remove unused road i18n keys from zh_cn.json and en_us.json."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UNUSED = ROOT / "tools/.tmp_unused_road_i18n.txt"
ZH = ROOT / "src/main/resources/assets/plot/lang/zh_cn.json"
EN = ROOT / "src/main/resources/assets/plot/lang/en_us.json"


def prune(path: Path, unused: set[str]) -> int:
    data = json.loads(path.read_text(encoding="utf-8"))
    removed = 0
    for key in unused:
        if key in data:
            del data[key]
            removed += 1
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return removed


def main() -> None:
    unused = {line.strip() for line in UNUSED.read_text(encoding="utf-8").splitlines() if line.strip()}
    zh_n = prune(ZH, unused)
    en_n = prune(EN, unused)
    print(f"removed zh={zh_n} en={en_n} from {len(unused)} unused candidates")


if __name__ == "__main__":
    main()
