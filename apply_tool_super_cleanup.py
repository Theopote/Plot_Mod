#!/usr/bin/env python3
"""Remove unused Chinese name/description args from DrawingTool/ModifyTool super() calls."""
import re
from pathlib import Path

ROOT = Path(__file__).parent
TOOLS = ROOT / "src/main/java/com/plot/ui/tools/impl"

# super("id", "label", Icon.X, "desc", ... -> super("id", Icon.X, ...
PATTERN = re.compile(
    r'super\(\s*'
    r'((?:\"[^\"]+\"|[A-Z_][A-Z0-9_]*))\s*,\s*'
    r'\"[^\"]*\"\s*,\s*'
    r'((?:Icons\.\w+|ToolPanelIcons\.\w+))\s*,\s*'
    r'\"[^\"]*\"'
    r'(\s*,)?',
    re.MULTILINE,
)


def transform(content: str) -> tuple[str, int]:
    new_content, count = PATTERN.subn(r"super(\1, \2\3", content)
    return new_content, count


def main() -> None:
    total = 0
    for path in sorted(TOOLS.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        updated, count = transform(text)
        if count:
            path.write_text(updated, encoding="utf-8")
            print(f"{path.relative_to(ROOT)}: {count} replacements")
            total += count
    print(f"Done. Total replacements: {total}")


if __name__ == "__main__":
    main()
