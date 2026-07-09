#!/usr/bin/env python3
"""Scan Java sources for PlotI18n keys and report missing entries in lang JSON files."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).parent
SRC = ROOT / "src/main/java/com/plot"
LANG_DIR = ROOT / "src/main/resources/assets/plot/lang"

KEY_PATTERN = re.compile(
    r'PlotI18n\.(?:tr|error|status|operationName|localizeMessage|localizeStatus)\(\s*"([a-z][a-z0-9_.]+)"'
)
ENUM_KEY_PATTERN = re.compile(r'"([a-z][a-z0-9_.]*)"')
TOOL_LABEL_PATTERN = re.compile(r'PlotI18n\.tool(?:Label|Description)\(\s*"([^"]+)"\s*\)')


def collect_java_keys() -> set[str]:
    keys: set[str] = set()
    for path in SRC.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        for key in KEY_PATTERN.findall(text):
            if not key.endswith("."):
                keys.add(key)
        for match in re.finditer(
            r'(?:ErrorCode|Context|BlockCategory|SearchScope|CategoryType)\(\s*"([a-z][a-z0-9_.]+)"\s*\)',
            text,
        ):
            keys.add(match.group(1))
    return keys


def load_lang(name: str) -> dict[str, str]:
    path = LANG_DIR / name
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    java_keys = sorted(collect_java_keys())
    en = load_lang("en_us.json")
    zh = load_lang("zh_cn.json")

    missing_en = [k for k in java_keys if k not in en]
    missing_zh = [k for k in java_keys if k not in zh]
    extra_en = sorted(set(en) - set(java_keys))

    print(f"Java PlotI18n keys referenced: {len(java_keys)}")
    print(f"en_us.json entries: {len(en)}")
    print(f"zh_cn.json entries: {len(zh)}")
    print()

    if missing_en:
        print(f"Missing in en_us.json ({len(missing_en)}):")
        for key in missing_en:
            print(f"  - {key}")
        print()
    else:
        print("All referenced keys exist in en_us.json")

    if missing_zh:
        print(f"Missing in zh_cn.json ({len(missing_zh)}):")
        for key in missing_zh:
            print(f"  - {key}")
        print()
    else:
        print("All referenced keys exist in zh_cn.json")

    # Keys in lang but never referenced (informational, capped)
    orphan = [k for k in extra_en if k.startswith(("status.plot.", "error.plot.", "dialog.plot.", "panel.plot.", "history.plot.", "tool.plot.", "toolbar.plot.", "gallery.plot.", "block.plot."))]
    if orphan:
        print(f"Potentially unused lang keys (sample, max 20 of {len(orphan)}):")
        for key in orphan[:20]:
            print(f"  ? {key}")

    return 1 if (missing_en or missing_zh) else 0


if __name__ == "__main__":
    sys.exit(main())
