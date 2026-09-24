#!/usr/bin/env python3
"""Audit powerline plugin i18n keys against Java references."""
import json
import os
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"

with open(ROOT / "src/main/resources/assets/plot/lang/zh_cn.json", encoding="utf-8") as f:
    zh = json.load(f)

keys = sorted(k for k in zh if k.startswith("plugin.powerline.") or k.startswith("hint.plot.powerline."))

literal_keys = set()
prefix_patterns = set()

for root, _, files in os.walk(SRC):
    for fn in files:
        if not fn.endswith(".java"):
            continue
        path = os.path.join(root, fn)
        with open(path, encoding="utf-8", errors="ignore") as fh:
            text = fh.read()
        for match in re.finditer(r'"((?:plugin\.powerline|hint\.plot\.powerline)\.[^"]+)"', text):
            literal_keys.add(match.group(1))
        for match in re.finditer(r'"((?:plugin\.powerline|hint\.plot\.powerline)\.[^"]+\.)"\s*\+', text):
            prefix_patterns.add(match.group(1))


def enum_constants(java_path: str) -> list[str]:
    text = (SRC / java_path).read_text(encoding="utf-8")
    body = text.split("{", 1)[1].rsplit("}", 1)[0]
    return re.findall(r"\b([A-Z][A-Z0-9_]*)\s*(?:\(|,|;)", body)


def add_keys(prefix: str, java_path: str, *, lower: bool = False):
    for name in enum_constants(java_path):
        suffix = name.lower() if lower else name
        literal_keys.add(prefix + suffix)


def add_style_pack_keys():
    catalog = (SRC / "main/java/com/plot/plugin/powerline/style/PowerLineStylePresetCatalog.java").read_text(encoding="utf-8")
    for match in re.finditer(r'"(plugin\.powerline\.style\.pack\.[^"]+)"', catalog):
        label = match.group(1)
        literal_keys.add(label)
        literal_keys.add(label + ".desc")


add_style_pack_keys()
add_keys("plugin.powerline.style.category.", "main/java/com/plot/plugin/powerline/style/StyleCategory.java", lower=True)
add_keys("plugin.powerline.route.spacing.", "main/java/com/plot/plugin/powerline/ui/PowerLineUiPresets.java", lower=True)
add_keys("plugin.powerline.route.placement_mode.", "main/java/com/plot/plugin/powerline/model/PoleSpacingMode.java")
add_keys("plugin.powerline.design.decoration.", "main/java/com/plot/plugin/powerline/design/structure/TowerDecorationKind.java", lower=True)
add_keys("plugin.powerline.style.sag.", "main/java/com/plot/plugin/powerline/ui/PowerLineUiPresets.java", lower=True)


def is_referenced(key: str) -> bool:
    if key in literal_keys:
        return True
    for prefix in prefix_patterns:
        if key.startswith(prefix):
            return True
    return False


unused = [k for k in keys if not is_referenced(k)]
used = [k for k in keys if is_referenced(k)]

print(f"Total: {len(keys)}")
print(f"Used: {len(used)}")
print(f"Unused: {len(unused)}")
print("--- UNUSED ---")
for key in unused:
    print(key)

groups = defaultdict(list)
for key in unused:
    parts = key.split(".")
    group = ".".join(parts[:3]) + "." if len(parts) >= 3 else "other"
    groups[group].append(key)

print("--- GROUPS ---")
for group, group_keys in sorted(groups.items(), key=lambda item: -len(item[1])):
    print(f"{group} ({len(group_keys)})")

if __name__ == "__main__":
    out = ROOT / "tools" / "powerline_i18n_unused.txt"
    out.write_text("\n".join(unused), encoding="utf-8")
    print(f"\nWrote {len(unused)} keys to {out}")
