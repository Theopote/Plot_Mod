#!/usr/bin/env python3
"""Find unused road-related i18n keys (plugin.road.*, preset.road.*, hint.plot.road.*)."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/plot/lang/zh_cn.json"
PREFIXES = ("plugin.road.", "preset.road.", "hint.plot.road.", "plugin.road_system.")


def collect_sources() -> str:
    parts: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        if path.suffix not in {".java", ".md", ".properties"}:
            continue
        if "build" in path.parts or ".gradle" in path.parts:
            continue
        try:
            if path.stat().st_size > 5_000_000:
                continue
            parts.append(path.read_text(encoding="utf-8", errors="ignore"))
        except OSError:
            pass
    return "\n".join(parts)


def dynamic_prefixes(blob: str) -> set[str]:
    patterns = [
        r'PlotI18n\.tr\(\s*["\']((plugin\.road\.|hint\.plot\.road\.|preset\.road\.)[^"\']*)["\']\s*\+',
        r'["\']((plugin\.road\.|hint\.plot\.road\.|preset\.road\.)[^"\']*)["\']\s*\+',
        r'\+ \s*["\']([^"\']+)["\']',
    ]
    found: set[str] = set()
    for pat in patterns:
        for m in re.finditer(pat, blob):
            found.add(m.group(1))
    return found


def key_used(key: str, blob: str, dyn: set[str]) -> bool:
    if f'"{key}"' in blob or f"'{key}'" in blob:
        return True
    for prefix in dyn:
        if key.startswith(prefix):
            return True
    # Known dynamic families — keep entire family if prefix referenced
    families = [
        "plugin.road.fix_road.issue.",
        "plugin.road.issue.",
        "plugin.road.validation.",
        "plugin.road.route.placement_mode.",
        "preset.road.",
    ]
    for family in families:
        if key.startswith(family) and family in blob:
            return True
    return False


def main() -> int:
    with LANG.open(encoding="utf-8") as f:
        all_keys = json.load(f)
    road_keys = sorted(k for k in all_keys if k.startswith(PREFIXES))
    blob = collect_sources()
    dyn = dynamic_prefixes(blob)
    unused = [k for k in road_keys if not key_used(k, blob, dyn)]
    print(f"Road-related keys: {len(road_keys)}")
    print(f"Unused: {len(unused)}")
    for k in unused:
        print(k)
    return 0


if __name__ == "__main__":
    sys.exit(main())
