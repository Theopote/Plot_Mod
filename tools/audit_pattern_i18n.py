#!/usr/bin/env python3
"""Audit pattern plugin i18n keys against Java references."""
import json
import os
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"

with open(ROOT / "src/main/resources/assets/plot/lang/zh_cn.json", encoding="utf-8") as f:
    zh = json.load(f)

keys = sorted(k for k in zh if k.startswith("plugin.pattern.") or k.startswith("hint.plot.pattern."))

literal_keys = set()
prefix_patterns = set()

for root, _, files in os.walk(SRC):
    for fn in files:
        if not fn.endswith(".java"):
            continue
        path = os.path.join(root, fn)
        with open(path, encoding="utf-8", errors="ignore") as fh:
            text = fh.read()
        for match in re.finditer(r'"((?:plugin\.pattern|hint\.plot\.pattern)\.[^"]+)"', text):
            literal_keys.add(match.group(1))
        for match in re.finditer(r'"((?:plugin\.pattern|hint\.plot\.pattern)\.[^"]+\.)"\s*\+', text):
            prefix_patterns.add(match.group(1))


def enum_constants(java_path: str) -> list[str]:
    text = (SRC / java_path).read_text(encoding="utf-8")
    body = text.split("{", 1)[1].rsplit("}", 1)[0]
    return re.findall(r"\b([A-Z][A-Z0-9_]*)\s*(?:\(|,|;)", body)


def add_keys(prefix: str, java_path: str, *, lower: bool = False):
    for name in enum_constants(java_path):
        suffix = name.lower() if lower else name
        literal_keys.add(prefix + suffix)


def add_builtin_preset_keys():
    text = (SRC / "main/java/com/plot/plugin/pattern/model/BuiltInPatternPresets.java").read_text(encoding="utf-8")
    for match in re.finditer(r'"(plugin\.pattern\.preset\.builtin\.[^"]+)"', text):
        literal_keys.add(match.group(1))


def add_type_catalog_keys():
    text = (SRC / "main/java/com/plot/plugin/pattern/model/PatternTypeCatalog.java").read_text(encoding="utf-8")
    for match in re.finditer(r'"(plugin\.pattern\.type_category\.[^"]+)"', text):
        literal_keys.add(match.group(1))


add_builtin_preset_keys()
add_type_catalog_keys()
add_keys("plugin.pattern.type.", "main/java/com/plot/plugin/pattern/model/ProceduralPatternConfig.java", lower=True)
add_keys("plugin.pattern.image_fit.", "main/java/com/plot/plugin/pattern/model/ImagePatternConfig.java", lower=True)

# PatternGenerationIssue enum keys.
issue_text = (SRC / "main/java/com/plot/plugin/pattern/PatternGenerationIssue.java").read_text(encoding="utf-8")
for match in re.finditer(r'"(plugin\.pattern\.issue\.[^"]+)"', issue_text):
    literal_keys.add(match.group(1))


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
    out = ROOT / "tools" / "pattern_i18n_unused.txt"
    out.write_text("\n".join(unused), encoding="utf-8")
    print(f"\nWrote {len(unused)} keys to {out}")
