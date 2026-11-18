#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/java/com/plot/plugin/road"
METHOD_RE = re.compile(
    r"(?m)^[ \t]*private(?:\s+static)?\s+(?:[\w.<>,\s\[\]?]+)\s+(\w+)\s*\("
)


def main() -> None:
    dead: list[tuple[str, str]] = []
    for path in ROOT.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="ignore")
        names = set(METHOD_RE.findall(text))
        for name in names:
            if name in {"hashCode", "equals", "toString", "writeReplace", "readResolve"}:
                continue
            if len(re.findall(rf"\b{re.escape(name)}\b", text)) == 1:
                dead.append((name, path.as_posix()))
    print(f"dead private methods: {len(dead)}")
    for name, path in sorted(dead, key=lambda x: x[1]):
        print(f"{name}\t{path}")


if __name__ == "__main__":
    main()
