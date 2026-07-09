#!/usr/bin/env python3
"""Merge batch-12 lang keys: toolbar groups and geometry validation errors."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    "toolbar.plot.group.view_tools": ("View Tools", "视图工具"),
    "toolbar.plot.group.control_sliders": ("Control Sliders", "控制滑动条"),
    "toolbar.plot.group.block_ops": ("Block Operations", "方块操作"),
    "toolbar.plot.group.tool_settings": ("Tool Settings", "工具设置"),
    "toolbar.plot.group.file_tools": ("File Tools", "文件工具"),
    "error.plot.shape.validation.position_null": ("Position cannot be null", "位置不能为空"),
    "error.plot.shape.validation.offset_null": ("Offset cannot be null", "偏移量不能为空"),
    "error.plot.shape.validation.rotation_center_null": ("Rotation center cannot be null", "旋转中心不能为空"),
    "error.plot.shape.validation.empty_data": ("Serialization data cannot be empty", "序列化数据不能为空"),
    "error.plot.shape.validation.deserialize_failed": ("Deserialization failed", "反序列化失败"),
    "error.plot.shape.validation.deserialize_number_format": (
        "Deserialization failed: invalid number format",
        "反序列化失败：数值格式错误",
    ),
    "error.plot.shape.validation.serialize_part_count": (
        "Invalid serialization format: expected %d parts, got %d",
        "序列化格式错误：需要%d个部分，实际：%d",
    ),
    "error.plot.shape.validation.scale_params_null": ("Scale parameters cannot be null", "缩放参数不能为空"),
    "error.plot.shape.validation.invalid_json": ("Invalid JSON format", "无效的JSON格式"),
    "error.plot.shape.validation.legacy_data_incomplete": (
        "Legacy format data is incomplete",
        "旧格式数据不完整",
    ),
    "error.plot.shape.validation.invalid_point_field": (
        "Invalid point format: %s",
        "无效的点格式: %s",
    ),
    "error.plot.shape.validation.invalid_number_field": (
        "Invalid number format: %s",
        "无效的数值格式: %s",
    ),
    "error.plot.shape.validation.invalid_int_field": (
        "Invalid integer format: %s",
        "无效的整数格式: %s",
    ),
    "error.plot.shape.validation.invalid_string_field": (
        "Invalid string format: %s",
        "无效的字符串格式: %s",
    ),
    "error.plot.text.no_extend_intersection": (
        "Text shapes do not support extend intersection calculation",
        "文本形状不支持延伸交点计算",
    ),
    "error.plot.text.no_tangent": (
        "Text shapes do not support tangent calculation",
        "文本形状不支持切线计算",
    ),
    "error.plot.text.no_split": ("Text shapes do not support split", "文本形状不支持分割操作"),
    "error.plot.text.no_extend": ("Text shapes do not support extend", "文本形状不支持延伸操作"),
    "error.plot.text.no_trim": ("Text shapes do not support trim", "文本形状不支持修剪操作"),
    "error.plot.text.no_offset": ("Text shapes do not support offset", "文本形状不支持偏移操作"),
    "error.plot.spiral.validation.negative_radius": (
        "Start radius cannot be negative: %s",
        "起始半径不能为负数: %s",
    ),
    "error.plot.spiral.validation.positive_turns": (
        "Turn count must be greater than 0: %s",
        "圈数必须大于0: %s",
    ),
    "error.plot.spiral.validation.positive_spacing": (
        "Spacing must be greater than 0: %s",
        "间距必须大于0: %s",
    ),
    "error.plot.spiral.validation.type_null": ("Spiral type cannot be null", "螺旋类型不能为null"),
    "error.plot.spiral.validation.null_segment_endpoint": (
        "Line segment endpoint cannot be null",
        "线段端点不能为null",
    ),
    "error.plot.spiral.validation.positive_coefficient": (
        "Spiral coefficient must be positive: %s",
        "螺旋系数必须为正数: %s",
    ),
    "error.plot.sine.validation.start_null": ("Start point cannot be null", "起始点不能为空"),
    "error.plot.sine.validation.end_null": ("End point cannot be null", "结束点不能为空"),
    "error.plot.sine.validation.amplitude_finite": (
        "Amplitude must be a finite number",
        "振幅必须是有限数值",
    ),
    "error.plot.sine.validation.wavelength_positive": (
        "Wavelength must be a finite number greater than 0",
        "波长必须是大于0的有限数值",
    ),
    "error.plot.sine.validation.phase_finite": ("Phase must be a finite number", "相位必须是有限数值"),
    "error.plot.sine.validation.extend_point_null": ("Extend point cannot be null", "延伸点不能为空"),
    "error.plot.sine.validation.extend_distance_negative": (
        "Extend distance cannot be negative",
        "延伸距离不能为负",
    ),
    "error.plot.sine.validation.target_null": ("Target point cannot be null", "目标点不能为空"),
    "error.plot.sine.validation.control_point_null": ("Control point cannot be null", "控制点不能为空"),
    "error.plot.sine.validation.invalid_control_index": (
        "Invalid control point index: %s",
        "无效的控制点索引: %s",
    ),
    "error.plot.bezier.validation.anchors_null": ("Anchor list cannot be null", "锚点列表不能为null"),
    "error.plot.bezier.validation.controls_null": ("Control point list cannot be null", "控制点列表不能为null"),
    "error.plot.bezier.validation.min_anchors": (
        "At least 2 anchor points are required",
        "锚点数量必须至少为2个",
    ),
    "error.plot.bezier.validation.control_pair_count": (
        "Control point pair count must be one less than anchor count",
        "控制点对数量必须比锚点数量少1",
    ),
    "error.plot.bezier.validation.null_anchor_index": (
        "Anchor point cannot be null at index: %s",
        "锚点不能为null，索引: %s",
    ),
    "error.plot.bezier.validation.null_control_array_index": (
        "Control point array cannot be null at index: %s",
        "控制点数组不能为null，索引: %s",
    ),
    "error.plot.bezier.validation.control_array_size": (
        "Each control point array must contain 2 points at index: %s",
        "每个控制点数组必须包含2个点，索引: %s",
    ),
    "error.plot.bezier.validation.null_control_index": (
        "Control point cannot be null at index: %s",
        "控制点不能为null，索引: %s",
    ),
    "error.plot.bezier.validation.min_closed_anchors": (
        "Closed curves require at least 3 anchor points",
        "闭合曲线至少需要3个锚点",
    ),
    "error.plot.bezier.validation.missing_fields": (
        "Invalid data format: missing required fields",
        "数据格式无效：缺少必要字段",
    ),
    "error.plot.bezier.validation.unsupported_version": (
        "Unsupported serialization format version: %s",
        "不支持的序列化格式版本: %s",
    ),
    "error.plot.bezier.validation.invalid_closed_state": (
        "Invalid closed state: %s",
        "无效的闭合状态: %s",
    ),
    "error.plot.bezier.validation.invalid_segment_count": (
        "Invalid segment count: %s",
        "无效的段数: %s",
    ),
    "error.plot.bezier.validation.negative_segment_count": (
        "Segment count cannot be negative: %s",
        "段数不能为负数: %s",
    ),
    "error.plot.bezier.validation.deserialize_failed": (
        "Deserialization failed: %s",
        "反序列化失败: %s",
    ),
    "error.plot.bezier.validation.invalid_point_format": (
        "Invalid point format: %s",
        "无效的点格式: %s",
    ),
    "error.plot.bezier.validation.invalid_coordinates": (
        "Invalid coordinate values: %s",
        "无效的坐标值: %s",
    ),
    "error.plot.bezier.validation.invalid_legacy_data": (
        "Invalid legacy format data",
        "无效的旧格式数据",
    ),
    "error.plot.bezier.validation.invalid_legacy_point_count": (
        "Invalid point count in legacy format data",
        "旧格式数据中点数无效",
    ),
    "error.plot.ellipse.validation.not_ellipse_after_scale": (
        "Scaled quadratic form is not an ellipse",
        "缩放后的二次型不是椭圆",
    ),
    "error.plot.matrix.singular": ("Matrix is singular", "矩阵不可逆"),
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
