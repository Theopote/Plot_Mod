#!/usr/bin/env python3
"""Merge batch-11 lang keys: element types and annotation shape errors."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    "element.plot.unknown": ("Unknown Element", "未知元素"),
    "element.plot.point": ("Point", "点"),
    "element.plot.line": ("Line", "线段"),
    "element.plot.rectangle": ("Rectangle", "矩形"),
    "element.plot.circle": ("Circle", "圆形"),
    "element.plot.ellipse": ("Ellipse", "椭圆"),
    "element.plot.polygon": ("Polygon", "多边形"),
    "element.plot.path": ("Path", "路径"),
    "element.plot.text": ("Text", "文本"),
    "element.plot.image": ("Image", "图像"),
    "element.plot.group": ("Group", "组"),
    "error.plot.annotation.no_rotate": (
        "Annotation shapes do not support rotation",
        "标注图形不支持旋转操作",
    ),
    "error.plot.annotation.no_transform": (
        "Annotation shapes do not support transform",
        "标注图形不支持变换操作",
    ),
    "error.plot.annotation.no_extend_intersection": (
        "Annotation shapes do not support extend intersection calculation",
        "标注图形不支持延伸交点计算",
    ),
    "error.plot.annotation.no_tangent": (
        "Annotation shapes do not support tangent calculation",
        "标注图形不支持切线计算",
    ),
    "error.plot.annotation.no_split": (
        "Annotation shapes do not support split",
        "标注图形不支持分割操作",
    ),
    "error.plot.annotation.no_extend": (
        "Annotation shapes do not support extend",
        "标注图形不支持延伸操作",
    ),
    "error.plot.annotation.no_trim": (
        "Annotation shapes do not support trim",
        "标注图形不支持修剪操作",
    ),
    "error.plot.annotation.no_offset": (
        "Annotation shapes do not support offset",
        "标注图形不支持偏移操作",
    ),
    "error.plot.annotation.no_break": (
        "Annotation shapes do not support break",
        "标注图形不支持打断操作",
    ),
    "error.plot.annotation.no_visitor": (
        "Annotation shapes do not support visitor mode",
        "标注图形不支持访问者模式",
    ),
    "error.plot.annotation.serialize_failed": (
        "Failed to serialize annotation shape",
        "标注图形序列化失败",
    ),
    "error.plot.annotation.empty_data": (
        "Serialization data cannot be empty",
        "序列化数据不能为空",
    ),
    "error.plot.annotation.parse_failed": (
        "Unable to parse annotation data",
        "无法解析标注数据",
    ),
    "error.plot.annotation.missing_type": (
        "Missing annotation type",
        "缺少标注类型",
    ),
    "error.plot.annotation.deserialize_failed": (
        "Failed to deserialize annotation shape: %s",
        "标注图形反序列化失败: %s",
    ),
    "error.plot.annotation.distance_missing_points": (
        "Distance annotation is missing endpoint data",
        "距离标注缺少端点数据",
    ),
    "error.plot.annotation.angle_missing_vertex": (
        "Angle annotation is missing vertex data",
        "角度标注缺少顶点数据",
    ),
    "error.plot.annotation.radius_missing_data": (
        "Radius annotation is missing center or radius",
        "半径标注缺少圆心或半径",
    ),
    "error.plot.annotation.area_missing_position": (
        "Area annotation is missing position data",
        "面积标注缺少位置数据",
    ),
    "error.plot.annotation.unknown_type": (
        "Unknown annotation type: %s",
        "未知标注类型: %s",
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
