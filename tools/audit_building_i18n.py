#!/usr/bin/env python3
"""Audit building plugin i18n keys against Java references."""
import json
import os
import re
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

with open(os.path.join(ROOT, "src/main/resources/assets/plot/lang/zh_cn.json"), encoding="utf-8") as f:
    zh = json.load(f)

keys = sorted(k for k in zh if k.startswith("plugin.building.") or k.startswith("hint.plot.building."))

literal_keys = set()
prefix_patterns = set()

for root, _, files in os.walk(os.path.join(ROOT, "src")):
    for fn in files:
        if not fn.endswith(".java"):
            continue
        path = os.path.join(root, fn)
        with open(path, encoding="utf-8", errors="ignore") as fh:
            text = fh.read()
        for match in re.finditer(r'"((?:plugin\.building|hint\.plot\.building)\.[^"]+)"', text):
            literal_keys.add(match.group(1))
        for match in re.finditer(r'"((?:plugin\.building|hint\.plot\.building)\.[^"]+\.)"\s*\+', text):
            prefix_patterns.add(match.group(1))

# Only prefixes with verified dynamic suffix assembly in code.
verified_prefixes = [
    "plugin.building.height_mode.",
    "plugin.building.adopt_reject.",
]


def is_referenced(key: str) -> bool:
    if key in literal_keys:
        return True
    for prefix in verified_prefixes:
        if key.startswith(prefix):
            return True
    for prefix in prefix_patterns:
        if key.startswith(prefix):
            return True
    return False


unused = [k for k in keys if not is_referenced(k)]

print(f"Total: {len(keys)}")
print(f"Used: {len(keys) - len(unused)}")
print(f"Unused: {len(unused)}")
print("--- UNUSED ---")
for key in unused:
    print(key)

if __name__ == "__main__":
    out = os.path.join(ROOT, "tools", "building_i18n_unused.txt")
    with open(out, "w", encoding="utf-8") as fh:
        fh.write("\n".join(unused))
    print(f"\nWrote {len(unused)} keys to {out}")
