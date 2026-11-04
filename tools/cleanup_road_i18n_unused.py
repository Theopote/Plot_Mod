#!/usr/bin/env python3
"""Remove unused road-plugin i18n keys with dynamic-family awareness."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG_FILES = [
    ROOT / "src/main/resources/assets/plot/lang/zh_cn.json",
    ROOT / "src/main/resources/assets/plot/lang/en_us.json",
]
PREFIXES = (
    "plugin.road.",
    "plugin.road_system.",
    "hint.plot.road.",
    "preset.road.",
    "status.plot.road.",
)


def load_source_blob() -> str:
    chunks: list[str] = []
    for root in (ROOT / "src/main/java", ROOT / "src/test/java"):
        for path in root.rglob("*"):
            if path.suffix in {".java", ".kt"} and path.is_file():
                chunks.append(path.read_text(encoding="utf-8", errors="ignore"))
    return "\n".join(chunks)


def catalog_issue_ids(blob_catalog: str) -> set[str]:
    ids = set(re.findall(r'IssueTemplate\.(?:ok|warning|error)\("([^"]+)"', blob_catalog))
    ids |= set(re.findall(r'"([a-z0-9_]+)",\s*RoadValidationAction', blob_catalog))
    ids |= set(re.findall(r'RoadValidationMessage\.of\([^,]+,\s*"([^"]+)"', blob_catalog))
    ids.add("unknown")
    return ids


def compute_used(road_keys: list[str], blob: str) -> set[str]:
    used: set[str] = set()
    for key in road_keys:
        if f'"{key}"' in blob or f"'{key}'" in blob:
            used.add(key)

    dyn_prefixes: set[str] = set()
    for pattern in (
        r'["\']((?:plugin\.road\.|hint\.plot\.road\.|preset\.road\.|status\.plot\.road\.|plugin\.road_system\.)[^"\']*)["\']\s*\+',
        r'PlotI18n\.(?:tr|status)\(\s*["\']((?:plugin\.road\.|hint\.plot\.road\.|preset\.road\.|status\.plot\.road\.)[^"\']*)["\']\s*\+',
    ):
        for match in re.finditer(pattern, blob):
            dyn_prefixes.add(match.group(1))

    # Dynamic stems expanded from catalogs / known suffixes below — do not
    # treat a bare family prefix as proof that every leaf key is live.
    guarded_dyn_stems = (
        "plugin.road.issue.",
        "plugin.road.fix_road.issue.",
        "plugin.road.issue.action.",
        "plugin.road.edit.tools.",
        "plugin.road.tunnel.style_preset.",
        "preset.road.",
    )

    for key in road_keys:
        if any(key.startswith(stem) for stem in guarded_dyn_stems):
            continue
        for prefix in dyn_prefixes:
            if key.startswith(prefix):
                used.add(key)
                break

    catalog_path = ROOT / "src/main/java/com/plot/plugin/road/validation/RoadValidationMessageCatalog.java"
    issue_ids = set()
    if catalog_path.exists():
        issue_ids |= catalog_issue_ids(catalog_path.read_text(encoding="utf-8"))
    # Also pick up RoadValidationMessage.of(..., "issue_id") across the codebase.
    issue_ids |= set(re.findall(
        r'RoadValidationMessage\.of\(\s*[^,]+,\s*"([a-z0-9_]+)"',
        blob,
    ))
    for issue_id in issue_ids:
        used.add(f"plugin.road.issue.{issue_id}.title")
        used.add(f"plugin.road.issue.{issue_id}.detail")

    repair_path = ROOT / "src/main/java/com/plot/plugin/road/repair/RoadRepairIssue.java"
    if repair_path.exists():
        text = repair_path.read_text(encoding="utf-8")
        for name in re.findall(r"\b([A-Z][A-Z0-9_]+)\b", text):
            base = "plugin.road.fix_road.issue." + name.lower()
            used.add(base)
            used.add(base + ".detail")

    for action in (
        "reconcile_intersections",
        "sync_segment_order",
        "snap_to_junction",
        "materialize_alignment",
        "smooth_grade",
        "make_short_roads_flat",
        "flat_to_junction_elevation",
        "allow_flat_roads_to_slope",
        "cancel_junction_elevation_change",
        "repair_road_topology",
    ):
        used.add(f"plugin.road.issue.action.{action}")

    for suffix in (
        "cross_section",
        "variable_cross_section",
        "centerline",
        "station_facilities",
        "diagnostics",
        "tunnel",
        "batch_cross_section",
        "batch_variable_cross_section",
        "batch_centerline",
        "batch_station_facilities",
        "batch_diagnostics",
        "batch_tunnel",
    ):
        used.add(f"plugin.road.edit.tools.{suffix}.title")
        used.add(f"plugin.road.edit.tools.{suffix}.subtitle")
        used.add(f"plugin.road.edit.tools.{suffix}.multi_hint")
    used.add("plugin.road.edit.tools.section")
    used.add("plugin.road.edit.tools.coming_soon")
    used.add("plugin.road.edit.tools.multi_single_only_hint")

    if "preset.road." in blob:
        used.update(key for key in road_keys if key.startswith("preset.road."))

    if "plugin.road.tunnel.style_preset." in blob:
        used.update(key for key in road_keys if key.startswith("plugin.road.tunnel.style_preset."))

    return used


def main() -> int:
    dry_run = "--dry-run" in sys.argv
    blob = load_source_blob()
    if "--list-dyn" in sys.argv:
        dyn_prefixes: set[str] = set()
        for pattern in (
            r'["\']((?:plugin\.road\.|hint\.plot\.road\.|preset\.road\.|status\.plot\.road\.|plugin\.road_system\.)[^"\']*)["\']\s*\+',
            r'PlotI18n\.(?:tr|status)\(\s*["\']((?:plugin\.road\.|hint\.plot\.road\.|preset\.road\.|status\.plot\.road\.)[^"\']*)["\']\s*\+',
        ):
            for match in re.finditer(pattern, blob):
                dyn_prefixes.add(match.group(1))
        for prefix in sorted(dyn_prefixes):
            print(prefix)
        print(f"count={len(dyn_prefixes)}")
        return 0
    with LANG_FILES[0].open(encoding="utf-8") as handle:
        zh = json.load(handle)
    road_keys = sorted(key for key in zh if key.startswith(PREFIXES))
    used = compute_used(road_keys, blob)
    unused = [key for key in road_keys if key not in used]

    list_path = ROOT / "tools" / "road_i18n_unused.txt"
    list_path.write_text("\n".join(unused) + ("\n" if unused else ""), encoding="utf-8")
    print(f"Road keys: {len(road_keys)}")
    print(f"Used: {len(set(road_keys) & used)}")
    print(f"Unused: {len(unused)}")
    print(f"Wrote {list_path.relative_to(ROOT)}")

    if dry_run:
        for key in unused:
            print(key)
        return 0

    for path in LANG_FILES:
        data = json.loads(path.read_text(encoding="utf-8"))
        removed = 0
        for key in unused:
            if key in data:
                del data[key]
                removed += 1
        path.write_text(
            json.dumps(data, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
            newline="\n",
        )
        remaining = sum(1 for key in data if key.startswith(PREFIXES))
        print(f"{path.name}: removed {removed}, remaining road-related={remaining}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
