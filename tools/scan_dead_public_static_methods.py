#!/usr/bin/env python3
"""Find public static methods in road package with no external call sites."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ROAD = ROOT / "src/main/java/com/plot/plugin/road"
SRC_JAVA = list((ROOT / "src").rglob("*.java"))

METHOD_RE = re.compile(
    r"(?m)^[ \t]*public\s+static\s+(?:[\w.<>,\s\[\]?]+)\s+(\w+)\s*\("
)


def main() -> None:
    blob_by_file = {
        p: p.read_text(encoding="utf-8", errors="ignore") for p in SRC_JAVA
    }
    all_blob = "\n".join(blob_by_file.values())
    dead: list[tuple[str, str]] = []
    for path in ROAD.rglob("*.java"):
        text = blob_by_file[path]
        for name in set(METHOD_RE.findall(text)):
            if name in {"main", "values", "valueOf"}:
                continue
            total = len(re.findall(rf"\b{re.escape(name)}\s*\(", all_blob))
            local = len(re.findall(rf"\b{re.escape(name)}\s*\(", text))
            # only definition-like local occurrence(s), no external calls
            if total <= local:
                dead.append((name, path.relative_to(ROOT).as_posix()))
    print(f"candidates: {len(dead)}")
    for name, path in sorted(dead, key=lambda x: x[1])[:80]:
        print(f"{name}\t{path}")


if __name__ == "__main__":
    main()
