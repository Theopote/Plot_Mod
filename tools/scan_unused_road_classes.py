#!/usr/bin/env python3
"""Find likely-unused public types under com.plot.plugin.road."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ROAD = ROOT / "src/main/java/com/plot/plugin/road"
SRC = ROOT / "src"

TYPE_RE = re.compile(
    r"^\s*(?:public\s+)?(?:final\s+)?(?:abstract\s+)?(class|interface|enum|record)\s+(\w+)",
    re.M,
)


def main() -> None:
    java_files = list(SRC.rglob("*.java"))
    blobs: dict[Path, str] = {
        p: p.read_text(encoding="utf-8", errors="ignore") for p in java_files
    }
    all_blob = "\n".join(blobs.values())

    candidates: list[tuple[str, Path, int]] = []
    for path in ROAD.rglob("*.java"):
        text = blobs[path]
        for match in TYPE_RE.finditer(text):
            kind, name = match.group(1), match.group(2)
            # skip nested-looking if lowercase start? keep all top-ish
            # Count references excluding defining file
            others = all_blob  # simpler whole search
            # require word boundary
            count = len(re.findall(rf"\b{re.escape(name)}\b", others))
            # defining file contributes some; if total <= occurrences in own file only roughly
            own = len(re.findall(rf"\b{re.escape(name)}\b", text))
            external = count - own
            if external <= 0:
                candidates.append((f"{kind} {name}", path, own))

    candidates.sort(key=lambda x: str(x[1]))
    print(f"candidates with no external refs: {len(candidates)}")
    for kind_name, path, own in candidates:
        rel = path.relative_to(ROOT)
        print(f"{kind_name}\t{rel}\town_refs={own}")


if __name__ == "__main__":
    main()
