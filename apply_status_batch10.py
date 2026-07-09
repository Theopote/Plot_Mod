#!/usr/bin/env python3
"""Merge batch-10 lang keys: layer events, project defaults, shortcut descriptions."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    "project.plot.unnamed": ("Untitled Project", "未命名项目"),
    "project.plot.default_canvas": ("Default Canvas", "默认画布"),
    "layer.plot.initial_name": ("Default Layer", "默认图层"),
    "layer.plot.fallback_name": ("Layer", "图层"),
    "layer.plot.event.created": ("Layer '%s' created", "图层 '%s' 已创建"),
    "layer.plot.event.removed": ("Layer '%s' removed", "图层 '%s' 已移除"),
    "layer.plot.event.property_changed": (
        "Layer '%s' property '%s' changed from '%s' to '%s'",
        "图层 '%s' 的属性 '%s' 从 '%s' 变更为 '%s'",
    ),
    "layer.plot.event.order_changed": (
        "Layer '%s' moved from position %d to %d",
        "图层 '%s' 从位置 %d 移动到位置 %d",
    ),
    "layer.plot.event.activated": (
        "Active layer switched from '%s' to '%s'",
        "活动图层从 '%s' 切换到 '%s'",
    ),
    "layer.plot.event.content_changed": (
        "Layer '%s' content changed: %s",
        "图层 '%s' 的内容发生变更: %s",
    ),
    "layer.plot.event.select_all": (
        "Select all elements in layer '%s'",
        "选择图层 '%s' 中的所有元素",
    ),
    "layer.plot.property.visibility": ("visibility", "可见性"),
    "layer.plot.property.locked": ("locked", "锁定"),
    "layer.plot.property.active": ("active", "活动状态"),
    "layer.plot.property.opacity": ("opacity", "不透明度"),
    "layer.plot.property.zOrder": ("z-order", "层级顺序"),
    "layer.plot.property.color": ("color", "颜色"),
    "layer.plot.property.lineStyle": ("line style", "线型"),
    "layer.plot.property.name": ("name", "名称"),
    "layer.plot.event.content.element_added": ("element added", "添加元素"),
    "layer.plot.event.content.element_removed": ("element removed", "移除元素"),
    "layer.plot.event.content.content_cleared": ("content cleared", "清空内容"),
    "layer.plot.event.content.shape_added": ("shape added", "添加图形"),
    "layer.plot.event.content.shape_removed": ("shape removed", "移除图形"),
    "shortcut.plot.delete.desc": ("Handles Delete shortcut", "处理删除快捷键"),
    "shortcut.plot.edit.desc": (
        "Handles undo/redo shortcuts",
        "处理基本编辑操作（撤销、重做）的快捷键",
    ),
    "shortcut.plot.escape.desc": (
        "Handles Escape to cancel operations",
        "处理 Escape 键取消操作",
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
