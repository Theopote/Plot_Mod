#!/usr/bin/env python3
"""Merge batch-9 lang keys: ghost blocks, projection command, line-to-block progress, project restore."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    "status.plot.line_to_block.processing_start": (
        "Processing %d shapes...",
        "开始处理 %d 个图形...",
    ),
    "status.plot.line_to_block.shape_progress": (
        "Processing shape %d/%d: generating %d blocks",
        "处理图形 %d/%d: 生成 %d 个方块",
    ),
    "status.plot.line_to_block.processing_done": (
        "Done: %d shapes, %d blocks generated",
        "处理完成: %d 个图形，共生成 %d 个方块",
    ),
    "status.plot.ghost.added": (
        "Added ghost block: %s at (%.2f, %.2f, %.2f)",
        "已添加幽灵方块: %s 在位置 (%.2f, %.2f, %.2f)",
    ),
    "status.plot.ghost.cleared": ("Cleared %d ghost blocks", "已清理 %d 个幽灵方块"),
    "status.plot.projection.redo": ("Redid projection for %d blocks", "已重做投影 %d 个方块"),
    "status.plot.projection.undo": ("Undid projection for %d blocks", "已撤销投影 %d 个方块"),
    "status.plot.projection.completed": (
        "Successfully projected %d blocks, skipped %d",
        "已成功投影 %d 个方块，跳过 %d 个",
    ),
    "status.plot.projection.no_ghost_blocks": (
        "No ghost blocks available to project (may be too far, chunk unloaded, or not in creative mode)",
        "没有可投影的幽灵方块（可能距离过远、区块未加载或不在创造模式）",
    ),
    "status.plot.project.restore.annotations_only": (
        "%d annotation shapes could not be restored; please recreate them.",
        "有 %d 个标注图形未能恢复，请重新创建。",
    ),
    "status.plot.project.restore.with_annotations": (
        "%d shapes could not be restored (%d annotations).",
        "有 %d 个图形未能恢复（其中 %d 个为标注）。",
    ),
    "status.plot.project.restore.generic": (
        "%d shapes could not be restored.",
        "有 %d 个图形未能恢复。",
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
