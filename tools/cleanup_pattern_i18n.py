#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
unused_path = ROOT / "tools" / "pattern_i18n_unused.txt"
unused = [line.strip() for line in unused_path.read_text(encoding="utf-8").splitlines() if line.strip()]

for relative in (
    "src/main/resources/assets/plot/lang/zh_cn.json",
    "src/main/resources/assets/plot/lang/en_us.json",
):
    path = ROOT / relative
    data = json.loads(path.read_text(encoding="utf-8"))
    removed = 0
    for key in unused:
        if key in data:
            del data[key]
            removed += 1
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    remaining = sum(
        1
        for key in data
        if key.startswith("plugin.pattern.") or key.startswith("hint.plot.pattern.")
    )
    print(f"{relative}: removed {removed}, remaining pattern keys={remaining}")
