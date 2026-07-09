#!/usr/bin/env python3
"""Batch-12: migrate geometry exception messages and merge lang keys."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"
GEOMETRY = ROOT / "src/main/java/com/plot/core/geometry"

JAVA_REPLACEMENTS: dict[str, list[tuple[str, str]]] = {
    "shapes/TextShape.java": [
        ('throw new IllegalArgumentException("位置不能为空")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.null_position"))'),
        ('throw new IllegalArgumentException("偏移量不能为空")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.null_offset"))'),
        ('throw new IllegalArgumentException("旋转中心不能为空")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.null_rotation_center"))'),
        ('throw new UnsupportedOperationException("文本形状不支持延伸交点计算")',
         'throw new UnsupportedOperationException(PlotI18n.tr("error.plot.text_shape.no_extend_intersection"))'),
        ('throw new UnsupportedOperationException("文本形状不支持切线计算")',
         'throw new UnsupportedOperationException(PlotI18n.tr("error.plot.text_shape.no_tangent"))'),
        ('throw new UnsupportedOperationException("文本形状不支持分割操作")',
         'throw new UnsupportedOperationException(PlotI18n.tr("error.plot.text_shape.no_split"))'),
        ('throw new UnsupportedOperationException("文本形状不支持延伸操作")',
         'throw new UnsupportedOperationException(PlotI18n.tr("error.plot.text_shape.no_extend"))'),
        ('throw new UnsupportedOperationException("文本形状不支持修剪操作")',
         'throw new UnsupportedOperationException(PlotI18n.tr("error.plot.text_shape.no_trim"))'),
        ('throw new UnsupportedOperationException("文本形状不支持偏移操作")',
         'throw new UnsupportedOperationException(PlotI18n.tr("error.plot.text_shape.no_offset"))'),
        ('throw new IllegalArgumentException("序列化数据不能为空")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.empty_serialization"))'),
        ('throw new RuntimeException("反序列化失败", ex)',
         'throw new RuntimeException(PlotI18n.tr("error.plot.shape.deserialize_failed"), ex)'),
        ('throw new RuntimeException("反序列化失败", e)',
         'throw new RuntimeException(PlotI18n.tr("error.plot.shape.deserialize_failed"), e)'),
        ('throw new IllegalArgumentException("反序列化失败：数值格式错误", e)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.deserialize_number_format"), e)'),
        ('throw new IllegalArgumentException("序列化格式错误：需要3个部分，实际：" + parts.length)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.serialization_parts", 3, parts.length))'),
        ('throw new IllegalArgumentException("缩放参数不能为空")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.null_scale_params"))'),
    ],
    "shapes/SpiralShape.java": [
        ('throw new IllegalArgumentException("起始半径不能为负数: " + radius)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.spiral.negative_radius", radius))'),
        ('throw new IllegalArgumentException("圈数必须大于0: " + turns)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.spiral.turns_positive", turns))'),
        ('throw new IllegalArgumentException("间距必须大于0: " + spacing)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.spiral.spacing_positive", spacing))'),
        ('throw new IllegalArgumentException("螺旋类型不能为null")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.spiral.type_required"))'),
        ('throw new NullPointerException("线段端点不能为null")',
         'throw new NullPointerException(PlotI18n.tr("error.plot.spiral.segment_null_endpoints"))'),
        ('throw new IllegalArgumentException("螺旋系数必须为正数: " + spiralCoefficient)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.spiral.coefficient_positive", spiralCoefficient))'),
    ],
    "shapes/SineCurveShape.java": [
        ('throw new IllegalArgumentException("起始点不能为空")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.sine.null_start"))'),
        ('throw new IllegalArgumentException("结束点不能为空")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.sine.null_end"))'),
        ('throw new IllegalArgumentException("振幅必须是有限数值")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.sine.amplitude_finite"))'),
        ('throw new IllegalArgumentException("波长必须是大于0的有限数值")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.sine.wavelength_positive"))'),
        ('throw new IllegalArgumentException("相位必须是有限数值")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.sine.phase_finite"))'),
        ('throw new RuntimeException("反序列化失败", ex)',
         'throw new RuntimeException(PlotI18n.tr("error.plot.shape.deserialize_failed"), ex)'),
        ('throw new IllegalArgumentException("延伸点不能为空")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.sine.null_extend_point"))'),
        ('throw new IllegalArgumentException("延伸距离不能为负")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.sine.negative_extend_distance"))'),
        ('throw new IllegalArgumentException("目标点不能为空")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.sine.null_target_point"))'),
        ('throw new IllegalArgumentException("控制点不能为空")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.sine.null_control_point"))'),
        ('throw new IllegalArgumentException("无效的控制点索引: " + index)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.sine.invalid_control_index", index))'),
    ],
    "shapes/CableShape.java": [
        ('throw new IllegalArgumentException("无效的JSON格式")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.invalid_json"))'),
        ('throw new IllegalArgumentException("旧格式数据不完整")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.legacy_data_incomplete"))'),
        ('throw new IllegalArgumentException("无效的点格式: " + field)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.invalid_point_format", field))'),
        ('throw new IllegalArgumentException("无效的数值格式: " + field)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.invalid_number_format", field))'),
        ('throw new IllegalArgumentException("无效的整数格式: " + field)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.invalid_integer_format", field))'),
        ('throw new IllegalArgumentException("无效的字符串格式: " + field)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.invalid_string_format", field))'),
    ],
    "shapes/EllipseShape.java": [
        ('throw new IllegalArgumentException("缩放后的二次型不是椭圆")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.ellipse.not_ellipse_after_scale"))'),
    ],
    "Matrix2d.java": [
        ('throw new IllegalStateException("矩阵不可逆")',
         'throw new IllegalStateException(PlotI18n.tr("error.plot.matrix.not_invertible"))'),
    ],
    "shapes/BezierCurveShape.java": [
        ('throw new IllegalArgumentException("锚点列表不能为null")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.null_anchors"))'),
        ('throw new IllegalArgumentException("控制点列表不能为null")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.null_controls"))'),
        ('throw new IllegalArgumentException("锚点数量必须至少为2个")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.min_anchors"))'),
        ('throw new IllegalArgumentException("控制点对数量必须比锚点数量少1")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.control_pair_count"))'),
        ('throw new IllegalArgumentException("锚点不能为null，索引: " + i)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.null_anchor_index", i))'),
        ('throw new IllegalArgumentException("控制点数组不能为null，索引: " + i)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.null_control_array", i))'),
        ('throw new IllegalArgumentException("每个控制点数组必须包含2个点，索引: " + i)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.control_pair_size", i))'),
        ('throw new IllegalArgumentException("控制点不能为null，索引: " + i)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.null_control_index", i))'),
        ('throw new IllegalArgumentException("闭合曲线至少需要3个锚点")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.closed_min_anchors"))'),
        ('validatePointValues(anchorPoints, "锚点")',
         'validatePointValues(anchorPoints, PlotI18n.tr("shape.plot.anchor_point"))'),
        ('validatePointValues(java.util.Arrays.asList(controls.get(i)), "控制点[" + i + "]")',
         'validatePointValues(java.util.Arrays.asList(controls.get(i)), PlotI18n.tr("error.plot.bezier.control_point_label", i))'),
        ('String.format("%s[%d]包含无效值: x=%f, y=%f", pointType, i, point.x, point.y)',
         'PlotI18n.tr("error.plot.bezier.invalid_point_values", pointType, i, point.x, point.y)'),
        ('throw new IllegalArgumentException("数据格式无效：缺少必要字段")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.missing_fields"))'),
        ('throw new IllegalArgumentException("不支持的序列化格式版本: " + parts[0])',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.unsupported_version", parts[0]))'),
        ('throw new IllegalArgumentException("无效的闭合状态: " + parts[1])',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.invalid_closed_state", parts[1]))'),
        ('throw new IllegalArgumentException("无效的段数: " + parts[2])',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.invalid_segment_count", parts[2]))'),
        ('throw new IllegalArgumentException("段数不能为负数: " + segmentCount)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.negative_segment_count", segmentCount))'),
        ('"数据长度不匹配：期望 %d 个字段，实际 %d 个字段", expectedParts, parts.length',
         '"error.plot.bezier.field_count_mismatch", expectedParts, parts.length'),
        ('throw new IllegalArgumentException("反序列化失败: " + e.getMessage(), e)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.deserialize_with_message", e.getMessage()), e)'),
        ('throw new IllegalArgumentException("无效的点格式: " + pointStr)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.invalid_point_string", pointStr))'),
        ('throw new IllegalArgumentException("无效的坐标值: " + pointStr, e)',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.bezier.invalid_coordinates", pointStr), e)'),
        ('throw new IllegalArgumentException("无效的旧格式数据")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.invalid_legacy_data"))'),
        ('throw new IllegalArgumentException("旧格式数据中点数无效")',
         'throw new IllegalArgumentException(PlotI18n.tr("error.plot.shape.invalid_legacy_point_count"))'),
    ],
}

KEYS = {
    "toolbar.plot.group.block_ops": ("Block Operations", "方块操作"),
    "toolbar.plot.group.tool_settings": ("Tool Settings", "工具设置"),
    "toolbar.plot.group.control_sliders": ("Control Sliders", "控制滑动条"),
    "toolbar.plot.group.view_tools": ("View Tools", "视图工具"),
    "toolbar.plot.group.file_tools": ("File Tools", "文件工具"),
    "shape.plot.anchor_point": ("Anchor point", "锚点"),
    "error.plot.shape.null_position": ("Position cannot be null", "位置不能为空"),
    "error.plot.shape.null_offset": ("Offset cannot be null", "偏移量不能为空"),
    "error.plot.shape.null_rotation_center": ("Rotation center cannot be null", "旋转中心不能为空"),
    "error.plot.shape.empty_serialization": ("Serialization data cannot be empty", "序列化数据不能为空"),
    "error.plot.shape.deserialize_failed": ("Deserialization failed", "反序列化失败"),
    "error.plot.shape.deserialize_number_format": ("Deserialization failed: invalid number format", "反序列化失败：数值格式错误"),
    "error.plot.shape.serialization_parts": ("Invalid serialization format: expected %d parts, got %d", "序列化格式错误：需要%d个部分，实际：%d"),
    "error.plot.shape.null_scale_params": ("Scale parameters cannot be null", "缩放参数不能为空"),
    "error.plot.shape.invalid_json": ("Invalid JSON format", "无效的JSON格式"),
    "error.plot.shape.legacy_data_incomplete": ("Legacy format data is incomplete", "旧格式数据不完整"),
    "error.plot.shape.invalid_point_format": ("Invalid point format: %s", "无效的点格式: %s"),
    "error.plot.shape.invalid_number_format": ("Invalid number format: %s", "无效的数值格式: %s"),
    "error.plot.shape.invalid_integer_format": ("Invalid integer format: %s", "无效的整数格式: %s"),
    "error.plot.shape.invalid_string_format": ("Invalid string format: %s", "无效的字符串格式: %s"),
    "error.plot.shape.invalid_legacy_data": ("Invalid legacy format data", "无效的旧格式数据"),
    "error.plot.shape.invalid_legacy_point_count": ("Invalid point count in legacy format data", "旧格式数据中点数无效"),
    "error.plot.shape.deserialize_with_message": ("Deserialization failed: %s", "反序列化失败: %s"),
    "error.plot.text_shape.no_extend_intersection": ("Text shapes do not support extend intersection calculation", "文本形状不支持延伸交点计算"),
    "error.plot.text_shape.no_tangent": ("Text shapes do not support tangent calculation", "文本形状不支持切线计算"),
    "error.plot.text_shape.no_split": ("Text shapes do not support split", "文本形状不支持分割操作"),
    "error.plot.text_shape.no_extend": ("Text shapes do not support extend", "文本形状不支持延伸操作"),
    "error.plot.text_shape.no_trim": ("Text shapes do not support trim", "文本形状不支持修剪操作"),
    "error.plot.text_shape.no_offset": ("Text shapes do not support offset", "文本形状不支持偏移操作"),
    "error.plot.spiral.negative_radius": ("Start radius cannot be negative: %s", "起始半径不能为负数: %s"),
    "error.plot.spiral.turns_positive": ("Turn count must be greater than 0: %s", "圈数必须大于0: %s"),
    "error.plot.spiral.spacing_positive": ("Spacing must be greater than 0: %s", "间距必须大于0: %s"),
    "error.plot.spiral.type_required": ("Spiral type cannot be null", "螺旋类型不能为null"),
    "error.plot.spiral.segment_null_endpoints": ("Segment endpoints cannot be null", "线段端点不能为null"),
    "error.plot.spiral.coefficient_positive": ("Spiral coefficient must be positive: %s", "螺旋系数必须为正数: %s"),
    "error.plot.sine.null_start": ("Start point cannot be null", "起始点不能为空"),
    "error.plot.sine.null_end": ("End point cannot be null", "结束点不能为空"),
    "error.plot.sine.amplitude_finite": ("Amplitude must be a finite number", "振幅必须是有限数值"),
    "error.plot.sine.wavelength_positive": ("Wavelength must be a finite number greater than 0", "波长必须是大于0的有限数值"),
    "error.plot.sine.phase_finite": ("Phase must be a finite number", "相位必须是有限数值"),
    "error.plot.sine.null_extend_point": ("Extend point cannot be null", "延伸点不能为空"),
    "error.plot.sine.negative_extend_distance": ("Extend distance cannot be negative", "延伸距离不能为负"),
    "error.plot.sine.null_target_point": ("Target point cannot be null", "目标点不能为空"),
    "error.plot.sine.null_control_point": ("Control point cannot be null", "控制点不能为空"),
    "error.plot.sine.invalid_control_index": ("Invalid control point index: %s", "无效的控制点索引: %s"),
    "error.plot.bezier.null_anchors": ("Anchor list cannot be null", "锚点列表不能为null"),
    "error.plot.bezier.null_controls": ("Control point list cannot be null", "控制点列表不能为null"),
    "error.plot.bezier.min_anchors": ("At least 2 anchor points are required", "锚点数量必须至少为2个"),
    "error.plot.bezier.control_pair_count": ("Control pair count must be one less than anchor count", "控制点对数量必须比锚点数量少1"),
    "error.plot.bezier.null_anchor_index": ("Anchor point cannot be null at index: %s", "锚点不能为null，索引: %s"),
    "error.plot.bezier.null_control_array": ("Control point array cannot be null at index: %s", "控制点数组不能为null，索引: %s"),
    "error.plot.bezier.control_pair_size": ("Each control point array must contain 2 points at index: %s", "每个控制点数组必须包含2个点，索引: %s"),
    "error.plot.bezier.null_control_index": ("Control point cannot be null at index: %s", "控制点不能为null，索引: %s"),
    "error.plot.bezier.closed_min_anchors": ("Closed curves require at least 3 anchor points", "闭合曲线至少需要3个锚点"),
    "error.plot.bezier.control_point_label": ("Control point [%s]", "控制点[%s]"),
    "error.plot.bezier.invalid_point_values": ("%s[%s] contains invalid values: x=%s, y=%s", "%s[%s]包含无效值: x=%s, y=%s"),
    "error.plot.bezier.missing_fields": ("Invalid data format: missing required fields", "数据格式无效：缺少必要字段"),
    "error.plot.bezier.unsupported_version": ("Unsupported serialization format version: %s", "不支持的序列化格式版本: %s"),
    "error.plot.bezier.invalid_closed_state": ("Invalid closed state: %s", "无效的闭合状态: %s"),
    "error.plot.bezier.invalid_segment_count": ("Invalid segment count: %s", "无效的段数: %s"),
    "error.plot.bezier.negative_segment_count": ("Segment count cannot be negative: %s", "段数不能为负数: %s"),
    "error.plot.bezier.field_count_mismatch": ("Field count mismatch: expected %d, got %d", "数据长度不匹配：期望 %d 个字段，实际 %d 个字段"),
    "error.plot.bezier.invalid_point_string": ("Invalid point format: %s", "无效的点格式: %s"),
    "error.plot.bezier.invalid_coordinates": ("Invalid coordinate values: %s", "无效的坐标值: %s"),
    "error.plot.ellipse.not_ellipse_after_scale": ("Scaled quadratic form is not an ellipse", "缩放后的二次型不是椭圆"),
    "error.plot.matrix.not_invertible": ("Matrix is not invertible", "矩阵不可逆"),
}


def ensure_plot_i18n_import(content: str) -> str:
    if "PlotI18n" not in content:
        return content
    if "import com.plot.utils.PlotI18n;" in content:
        return content
    match = re.search(r"(package [^;]+;\r?\n\r?\n)", content)
    if not match:
        return content
    insert_at = match.end()
    return content[:insert_at] + "import com.plot.utils.PlotI18n;\r\n" if "\r\n" in content[:insert_at] else content[:insert_at] + "import com.plot.utils.PlotI18n;\n"


def fix_bezier_field_mismatch(content: str) -> str:
    old = '''                throw new IllegalArgumentException(String.format(
                    "数据长度不匹配：期望 %d 个字段，实际 %d 个字段", expectedParts, parts.length));'''
    new = '''                throw new IllegalArgumentException(PlotI18n.tr(
                    "error.plot.bezier.field_count_mismatch", expectedParts, parts.length));'''
    return content.replace(old, new)


def apply_java() -> None:
    total = 0
    for rel_path, replacements in JAVA_REPLACEMENTS.items():
        path = GEOMETRY / rel_path
        content = path.read_text(encoding="utf-8")
        original = content
        for old, new in replacements:
            if old not in content:
                print(f"WARN missing in {rel_path}: {old[:60]}...")
                continue
            content = content.replace(old, new)
            total += 1
        if rel_path == "shapes/BezierCurveShape.java":
            content = fix_bezier_field_mismatch(content)
        if "PlotI18n" in content:
            content = ensure_plot_i18n_import(content)
        if content != original:
            path.write_text(content, encoding="utf-8")
            print(f"Updated {rel_path}")
    print(f"Applied {total} Java replacements")


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
    apply_java()
    merge_lang(LANG / "en_us.json", 0)
    merge_lang(LANG / "zh_cn.json", 1)
    print(f"Done. Keys in batch: {len(KEYS)}")
