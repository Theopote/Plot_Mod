#!/usr/bin/env python3
"""Remove unused road i18n keys from zh_cn.json and en_us.json."""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG_DIR = ROOT / "src/main/resources/assets/plot/lang"
FIND_SCRIPT = ROOT / "tools/find_unused_road_i18n.py"


def load_unused_keys() -> list[str]:
    result = subprocess.run(
        [sys.executable, str(FIND_SCRIPT)],
        capture_output=True,
        text=True,
        check=True,
        cwd=ROOT,
    )
    keys: list[str] = []
    for line in result.stdout.splitlines():
        line = line.strip()
        if line.startswith("plugin.") or line.startswith("hint.") or line.startswith("preset."):
            keys.append(line)
    return keys


def main() -> int:
    unused = load_unused_keys()
    if not unused:
        print("No unused keys to remove.")
        return 0

    for lang_name in ("zh_cn.json", "en_us.json"):
        path = LANG_DIR / lang_name
        with path.open(encoding="utf-8") as f:
            data = json.load(f)
        removed = 0
        for key in unused:
            if key in data:
                del data[key]
                removed += 1
        with path.open("w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print(f"{lang_name}: removed {removed} keys")

    print(f"Total keys targeted: {len(unused)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
