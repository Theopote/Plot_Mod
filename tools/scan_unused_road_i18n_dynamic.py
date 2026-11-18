#!/usr/bin/env python3
"""Find likely-unused keys under dynamic prefixes by collecting concrete suffixes from code."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ZH = ROOT / "src/main/resources/assets/plot/lang/zh_cn.json"

DYNAMIC = {
    "plugin.road.bridge_pier_style.": re.compile(
        r'plugin\.road\.bridge_pier_style\.(?:\"\s*\+|\"([a-z0-9_]+)\")'
    ),
    "plugin.road.edit.tools.": re.compile(
        r'"plugin\.road\.edit\.tools\.([a-z0-9_.]+)"'
    ),
    "plugin.road.fix_road.issue.": re.compile(
        r'"plugin\.road\.fix_road\.issue\.([a-z0-9_.]+)"'
    ),
    "plugin.road.issue.": re.compile(r'"plugin\.road\.issue\.([a-z0-9_.]+)"'),
    "plugin.road.issue.action.": re.compile(
        r'"plugin\.road\.issue\.action\.([a-z0-9_.]+)"'
    ),
    "plugin.road.tunnel.style_preset.": re.compile(
        r'"plugin\.road\.tunnel\.style_preset\.([a-z0-9_.]+)"'
    ),
    "preset.road.": re.compile(r'"preset\.road\.([a-z0-9_.]+)"'),
}


def main() -> None:
    zh = json.loads(ZH.read_text(encoding="utf-8"))
    java = "\n".join(
        p.read_text(encoding="utf-8", errors="ignore")
        for p in (ROOT / "src").rglob("*.java")
    )

    # Also collect issue ids passed as productIssueId("foo") etc.
    issue_ids = set(re.findall(r'productIssueId\("([a-z0-9_]+)"\)', java))
    issue_ids.update(re.findall(r'issueId\("([a-z0-9_]+)"\)', java))
    issue_ids.update(re.findall(r'"plugin\.road\.issue\.([a-z0-9_]+)\.title"', java))
    issue_ids.update(re.findall(r'"plugin\.road\.issue\.([a-z0-9_]+)\.detail"', java))

    # Enum-style: RoadFloatingEditorKind / Tool cards
    tool_ids = set(re.findall(r'"plugin\.road\.edit\.tools\.([a-z0-9_]+)\.', java))
    tool_ids.update(re.findall(r'edit\.tools\.([a-z0-9_]+)\.(?:title|subtitle)', java))

    # preset.road. from style ids
    style_ids = set(re.findall(r'setStyleId\("([a-z0-9_]+)"\)', java))
    style_ids.update(re.findall(r'"([a-z0-9_]+)"\s*,\s*RoadStyle', java))
    # from resources styles
    for p in (ROOT / "src/main/resources").rglob("*.json"):
        if "lang" in p.parts:
            continue
        text = p.read_text(encoding="utf-8", errors="ignore")
        style_ids.update(re.findall(r'"id"\s*:\s*"([a-z0-9_]+)"', text))
        style_ids.update(re.findall(r'"styleId"\s*:\s*"([a-z0-9_]+)"', text))

    unused: list[str] = []
    for key in sorted(zh):
        if key.startswith("plugin.road.issue.") and not key.startswith("plugin.road.issue.action."):
            # expect ...issue.<id>.title / .detail
            m = re.match(r"plugin\.road\.issue\.([a-z0-9_]+)\.(title|detail)$", key)
            if m and m.group(1) not in issue_ids:
                # still keep if full key literal exists
                if f'"{key}"' not in java:
                    unused.append(key)
            continue
        if key.startswith("plugin.road.issue.action."):
            action = key[len("plugin.road.issue.action.") :]
            if f'"{key}"' not in java and f'issue.action.{action}' not in java:
                # actions often from actionKey() built as prefix+id
                if action not in issue_ids and f'"plugin.road.issue.action.{action}"' not in java:
                    unused.append(key)
            continue
        if key.startswith("plugin.road.edit.tools."):
            m = re.match(r"plugin\.road\.edit\.tools\.([a-z0-9_]+)\.(title|subtitle|multi_hint)$", key)
            if m and m.group(1) not in tool_ids and f'"{key}"' not in java:
                unused.append(key)
            continue
        if key.startswith("preset.road."):
            sid = key[len("preset.road.") :]
            if sid not in style_ids and f'"{key}"' not in java and f'preset.road." +' not in java:
                # keep if style id appears anywhere as string
                if f'"{sid}"' not in java:
                    unused.append(key)
            continue
        if key.startswith("plugin.road.fix_road.issue."):
            if f'"{key}"' not in java:
                # suffix after prefix
                rest = key[len("plugin.road.fix_road.issue.") :]
                if f'fix_road.issue.{rest}' not in java and rest.split(".")[0] not in issue_ids:
                    unused.append(key)
            continue
        if key.startswith("plugin.road.bridge_pier_style."):
            if f'"{key}"' not in java:
                rest = key[len("plugin.road.bridge_pier_style.") :]
                if f'"{rest}"' not in java:
                    unused.append(key)
            continue
        if key.startswith("plugin.road.tunnel.style_preset."):
            if f'"{key}"' not in java:
                rest = key[len("plugin.road.tunnel.style_preset.") :]
                if f'"{rest}"' not in java:
                    unused.append(key)

    out = ROOT / "tools/.tmp_unused_road_i18n_dynamic.txt"
    out.write_text("\n".join(unused) + ("\n" if unused else ""), encoding="utf-8")
    print(f"dynamic unused candidates: {len(unused)}")
    for k in unused[:60]:
        print(k)
    if len(unused) > 60:
        print(f"... +{len(unused)-60} more")


if __name__ == "__main__":
    main()
