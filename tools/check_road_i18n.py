#!/usr/bin/env python3
"""Find unused road plugin i18n keys."""
import json
import os
import re
import sys

LANG = "src/main/resources/assets/plot/lang/zh_cn.json"
SOURCE_ROOTS = ["src/main/java", "src/test/java"]
ROAD_PREFIXES = ("plugin.road", "status.plot.road", "path.plot", "plugin.road_system")


def load_source_text() -> str:
    chunks = []
    for root in SOURCE_ROOTS:
        for dirpath, _, files in os.walk(root):
            for fn in files:
                if fn.endswith((".java", ".kt")):
                    path = os.path.join(dirpath, fn)
                    with open(path, encoding="utf-8", errors="ignore") as f:
                        chunks.append(f.read())
    return "\n".join(chunks)


def dynamic_keys() -> set[str]:
    keys: set[str] = set()
    catalog_path = "src/main/java/com/plot/plugin/road/validation/RoadValidationMessageCatalog.java"
    with open(catalog_path, encoding="utf-8") as f:
        catalog = f.read()

    issue_ids = set(re.findall(r'IssueTemplate\.(?:ok|warning|error)\("([^"]+)"', catalog))
    issue_ids |= set(re.findall(r'"([a-z0-9_]+)",\s*RoadValidationAction', catalog))
    issue_ids |= set(re.findall(r'RoadValidationMessage\.of\([^,]+,\s*"([^"]+)"', catalog))

    issue_ids.add("unknown")
    for issue_id in issue_ids:
        keys.add(f"plugin.road.issue.{issue_id}.title")
        keys.add(f"plugin.road.issue.{issue_id}.detail")

    repair_issues = [
        "intersection_incomplete",
        "intersection_pending",
        "topology_disconnected",
        "topology_branching",
        "topology_cycle",
        "segment_order_mismatch",
        "horizontal_alignment_mismatch",
        "junction_endpoint_mismatch",
        "vertical_profile_mismatch",
        "centerline_self_intersection",
        "centerline_self_overlap",
        "centerline_non_linear",
        "steep_grade",
    ]
    for issue in repair_issues:
        keys.add(f"plugin.road.fix_road.issue.{issue}")

    actions = [
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
    ]
    for action in actions:
        keys.add(f"plugin.road.issue.action.{action}")

    return keys


def main() -> int:
    with open(LANG, encoding="utf-8") as f:
        data = json.load(f)

    road_keys = sorted(k for k in data if any(k.startswith(p) for p in ROAD_PREFIXES))
    source_text = load_source_text()
    dynamic = dynamic_keys()

    unused = [k for k in road_keys if k not in source_text and k not in dynamic]
    print(f"Total road keys: {len(road_keys)}")
    print(f"Unused: {len(unused)}")
    for key in unused:
        print(key)

    if "--write" in sys.argv:
        for lang_file in [
            "src/main/resources/assets/plot/lang/zh_cn.json",
            "src/main/resources/assets/plot/lang/en_us.json",
        ]:
            with open(lang_file, encoding="utf-8") as f:
                lang = json.load(f)
            for key in unused:
                lang.pop(key, None)
            with open(lang_file, "w", encoding="utf-8", newline="\n") as f:
                json.dump(lang, f, ensure_ascii=False, indent=2)
                f.write("\n")
        print(f"Removed {len(unused)} keys from zh_cn.json and en_us.json")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
