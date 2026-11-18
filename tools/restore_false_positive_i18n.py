#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ZH = ROOT / "src/main/resources/assets/plot/lang/zh_cn.json"
EN = ROOT / "src/main/resources/assets/plot/lang/en_us.json"


def git_show(path: str) -> dict:
    raw = subprocess.check_output(
        ["git", "show", f"HEAD:{path}"],
        cwd=ROOT,
    )
    return json.loads(raw.decode("utf-8"))


def main() -> None:
    zh_now = json.loads(ZH.read_text(encoding="utf-8"))
    en_now = json.loads(EN.read_text(encoding="utf-8"))
    zh_old = git_show("src/main/resources/assets/plot/lang/zh_cn.json")
    en_old = git_show("src/main/resources/assets/plot/lang/en_us.json")

    blob = "\n".join(
        p.read_text(encoding="utf-8", errors="ignore")
        for p in (ROOT / "src").rglob("*.java")
    )
    concat = set(
        re.findall(
            r'"((?:plugin\.road|hint\.plot\.road|preset\.road)[^"]*)"\s*\+',
            blob,
        )
    )
    print("concat prefixes:", sorted(concat))

    deleted = sorted(
        k
        for k in zh_old
        if k not in zh_now
        and (
            k.startswith("plugin.road.")
            or k.startswith("hint.plot.road.")
            or k.startswith("preset.road.")
        )
    )
    restore = []
    for key in deleted:
        if key in blob:
            restore.append(key)
            continue
        if any(key.startswith(prefix) and key != prefix for prefix in concat):
            restore.append(key)

    print(f"deleted={len(deleted)} restore={len(restore)}")
    for key in restore:
        print(key)
        if key in zh_old:
            zh_now[key] = zh_old[key]
        if key in en_old:
            en_now[key] = en_old[key]

    ZH.write_text(json.dumps(zh_now, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    EN.write_text(json.dumps(en_now, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("restored")


if __name__ == "__main__":
    main()
