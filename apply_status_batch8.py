#!/usr/bin/env python3
"""Merge batch-8 lang keys: spiral types, text input hints, exceptions, block projection."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    # spiral types
    "mode.plot.spiral.linear": ("Linear Spiral", "线性螺旋"),
    "mode.plot.spiral.logarithmic": ("Logarithmic Spiral", "对数螺旋"),
    "mode.plot.spiral.semicircle": ("Semicircle Spiral", "半圆螺旋"),
    "mode.plot.spiral.fermat": ("Fermat Spiral", "费马螺旋"),
    "mode.plot.spiral.fibonacci": ("Fibonacci Spiral", "斐波那契"),
    "mode.plot.spiral.polygon": ("Polygon Spiral", "多边形螺旋"),
    # text input dialog
    "dialog.plot.text_input.native_hint": (
        "System input is open; you can type Chinese. The box below is preview only. ",
        "已打开系统输入框，可输入中文；下方仅显示预览。 ",
    ),
    "dialog.plot.text_input.fallback_hint": (
        "Native input is unavailable; using built-in input instead. ",
        "当前环境不支持系统输入框，已回退到内置输入。 ",
    ),
    # tool exceptions
    "status.plot.exception.resource_unavailable": (
        "Resource unavailable, please retry",
        "资源暂不可用，请重试",
    ),
    "status.plot.exception.tool_state_reset": (
        "Tool state error, resetting",
        "工具状态异常，正在重置",
    ),
    # block projection
    "status.plot.projection.client_not_ready": (
        "Minecraft client not ready",
        "Minecraft客户端未就绪",
    ),
    "status.plot.projection.preview_success": ("Preview successful", "预览成功"),
    "status.plot.projection.place_failed": (
        "Failed to place block at (%d, %d, %d)",
        "在位置 (%d, %d, %d) 放置方块失败",
    ),
    "status.plot.projection.success": ("Projection successful", "投影成功"),
    "status.plot.projection.too_far": (
        "Target (%d, %d, %d) is too far; move closer and retry (%.1f blocks)",
        "目标位置 (%d, %d, %d) 距离太远，请靠近后重试 (%.1f方块)",
    ),
    "status.plot.projection.chunk_not_loaded": (
        "Target (%d, %d, %d) is not loaded; move closer and retry",
        "目标位置 (%d, %d, %d) 尚未加载，请靠近后重试",
    ),
    "status.plot.projection.creative_required": (
        "Switch to creative mode and try again",
        "请切换到创造模式后再试",
    ),
}


def merge_lang(path: Path, lang_idx: int) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    added = 0
    for key, values in KEYS.items():
        if key not in data:
            data[key] = values[lang_idx]
            added += 1
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{path.name}: added {added} keys")


if __name__ == "__main__":
    merge_lang(LANG / "en_us.json", 0)
    merge_lang(LANG / "zh_cn.json", 1)
    print(f"Done. Total keys in batch: {len(KEYS)}")
