#!/usr/bin/env python3
import re
from pathlib import Path

ROOT = Path(__file__).parent / "src/main/java"
PLOT = "import com.plot.utils.PlotI18n;"
pat = re.compile(r'String\.format\("(status\.plot\.[^"]+)"([^)]*)\)')

for path in ROOT.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    new = pat.sub(lambda m: f'PlotI18n.status("{m.group(1)}"{m.group(2)})', text)
    if new != text:
        if "PlotI18n." in new and PLOT not in new:
            idx = new.rfind("import ")
            end = new.find(";", idx) + 1
            new = new[:end] + "\n" + PLOT + new[end:]
        path.write_text(new, encoding="utf-8")
        print(f"fixed {path.relative_to(ROOT)}")
