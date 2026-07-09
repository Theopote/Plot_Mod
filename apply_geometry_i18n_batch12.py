#!/usr/bin/env python3
"""Replace hardcoded Chinese geometry exception strings with PlotI18n.error() calls."""
from pathlib import Path

ROOT = Path(__file__).parent
SRC = ROOT / "src/main/java/com/plot/core/geometry"

# (old_string, new_string) per file relative to SRC
REPLACEMENTS = {
    "shapes/TextShape.java": [
        ('throw new IllegalArgumentException("位置不能为空")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.position_null"))'),
        ('throw new IllegalArgumentException("偏移量不能为空")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.offset_null"))'),
        ('throw new IllegalArgumentException("旋转中心不能为空")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.rotation_center_null"))'),
        ('throw new UnsupportedOperationException("文本形状不支持延伸交点计算")', 'throw new UnsupportedOperationException(PlotI18n.error("error.plot.text.no_extend_intersection"))'),
        ('throw new UnsupportedOperationException("文本形状不支持切线计算")', 'throw new UnsupportedOperationException(PlotI18n.error("error.plot.text.no_tangent"))'),
        ('throw new UnsupportedOperationException("文本形状不支持分割操作")', 'throw new UnsupportedOperationException(PlotI18n.error("error.plot.text.no_split"))'),
        ('throw new UnsupportedOperationException("文本形状不支持延伸操作")', 'throw new UnsupportedOperationException(PlotI18n.error("error.plot.text.no_extend"))'),
        ('throw new UnsupportedOperationException("文本形状不支持修剪操作")', 'throw new UnsupportedOperationException(PlotI18n.error("error.plot.text.no_trim"))'),
        ('throw new UnsupportedOperationException("文本形状不支持偏移操作")', 'throw new UnsupportedOperationException(PlotI18n.error("error.plot.text.no_offset"))'),
        ('throw new IllegalArgumentException("序列化数据不能为空")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.empty_data"))'),
        ('throw new RuntimeException("反序列化失败", ex)', 'throw new RuntimeException(PlotI18n.error("error.plot.shape.validation.deserialize_failed"), ex)'),
        ('throw new RuntimeException("反序列化失败", e)', 'throw new RuntimeException(PlotI18n.error("error.plot.shape.validation.deserialize_failed"), e)'),
        ('throw new IllegalArgumentException("反序列化失败：数值格式错误", e)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.deserialize_number_format"), e)'),
        ('throw new IllegalArgumentException("序列化格式错误：需要3个部分，实际：" + parts.length)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.serialize_part_count", 3, parts.length))'),
        ('throw new IllegalArgumentException("缩放参数不能为空")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.scale_params_null"))'),
    ],
    "shapes/SpiralShape.java": [
        ('throw new IllegalArgumentException("起始半径不能为负数: " + radius)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.negative_radius", radius))'),
        ('throw new IllegalArgumentException("圈数必须大于0: " + turns)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.positive_turns", turns))'),
        ('throw new IllegalArgumentException("间距必须大于0: " + spacing)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.positive_spacing", spacing))'),
        ('throw new IllegalArgumentException("螺旋类型不能为null")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.type_null"))'),
        ('throw new NullPointerException("线段端点不能为null")', 'throw new NullPointerException(PlotI18n.error("error.plot.spiral.validation.null_segment_endpoint"))'),
        ('throw new IllegalArgumentException("螺旋系数必须为正数: " + spiralCoefficient)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.spiral.validation.positive_coefficient", spiralCoefficient))'),
    ],
    "shapes/CableShape.java": [
        ('throw new IllegalArgumentException("无效的JSON格式")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.invalid_json"))'),
        ('throw new IllegalArgumentException("旧格式数据不完整")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.legacy_data_incomplete"))'),
        ('throw new IllegalArgumentException("无效的点格式: " + field)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.invalid_point_field", field))'),
        ('throw new IllegalArgumentException("无效的数值格式: " + field)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.invalid_number_field", field))'),
        ('throw new IllegalArgumentException("无效的整数格式: " + field)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.invalid_int_field", field))'),
        ('throw new IllegalArgumentException("无效的字符串格式: " + field)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.invalid_string_field", field))'),
    ],
    "shapes/SineCurveShape.java": [
        ('throw new IllegalArgumentException("起始点不能为空")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.sine.validation.start_null"))'),
        ('throw new IllegalArgumentException("结束点不能为空")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.sine.validation.end_null"))'),
        ('throw new IllegalArgumentException("振幅必须是有限数值")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.sine.validation.amplitude_finite"))'),
        ('throw new IllegalArgumentException("波长必须是大于0的有限数值")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.sine.validation.wavelength_positive"))'),
        ('throw new IllegalArgumentException("相位必须是有限数值")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.sine.validation.phase_finite"))'),
        ('throw new RuntimeException("反序列化失败", ex)', 'throw new RuntimeException(PlotI18n.error("error.plot.shape.validation.deserialize_failed"), ex)'),
        ('throw new IllegalArgumentException("延伸点不能为空")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.sine.validation.extend_point_null"))'),
        ('throw new IllegalArgumentException("延伸距离不能为负")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.sine.validation.extend_distance_negative"))'),
        ('throw new IllegalArgumentException("目标点不能为空")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.sine.validation.target_null"))'),
        ('throw new IllegalArgumentException("控制点不能为空")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.sine.validation.control_point_null"))'),
        ('throw new IllegalArgumentException("无效的控制点索引: " + index)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.sine.validation.invalid_control_index", index))'),
    ],
    "shapes/BezierCurveShape.java": [
        ('throw new IllegalArgumentException("锚点列表不能为null")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.anchors_null"))'),
        ('throw new IllegalArgumentException("控制点列表不能为null")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.controls_null"))'),
        ('throw new IllegalArgumentException("锚点数量必须至少为2个")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.min_anchors"))'),
        ('throw new IllegalArgumentException("控制点对数量必须比锚点数量少1")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.control_pair_count"))'),
        ('throw new IllegalArgumentException("锚点不能为null，索引: " + i)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.null_anchor_index", i))'),
        ('throw new IllegalArgumentException("控制点数组不能为null，索引: " + i)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.null_control_array_index", i))'),
        ('throw new IllegalArgumentException("每个控制点数组必须包含2个点，索引: " + i)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.control_array_size", i))'),
        ('throw new IllegalArgumentException("控制点不能为null，索引: " + i)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.null_control_index", i))'),
        ('throw new IllegalArgumentException("闭合曲线至少需要3个锚点")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.min_closed_anchors"))'),
        ('throw new IllegalArgumentException("数据格式无效：缺少必要字段")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.missing_fields"))'),
        ('throw new IllegalArgumentException("不支持的序列化格式版本: " + parts[0])', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.unsupported_version", parts[0]))'),
        ('throw new IllegalArgumentException("无效的闭合状态: " + parts[1])', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.invalid_closed_state", parts[1]))'),
        ('throw new IllegalArgumentException("无效的段数: " + parts[2])', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.invalid_segment_count", parts[2]))'),
        ('throw new IllegalArgumentException("段数不能为负数: " + segmentCount)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.negative_segment_count", segmentCount))'),
        ('throw new IllegalArgumentException("反序列化失败: " + e.getMessage(), e)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.deserialize_failed", e.getMessage()), e)'),
        ('throw new IllegalArgumentException("无效的点格式: " + pointStr)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.invalid_point_format", pointStr))'),
        ('throw new IllegalArgumentException("无效的坐标值: " + pointStr, e)', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.invalid_coordinates", pointStr), e)'),
        ('throw new IllegalArgumentException("无效的旧格式数据")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.invalid_legacy_data"))'),
        ('throw new IllegalArgumentException("旧格式数据中点数无效")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.bezier.validation.invalid_legacy_point_count"))'),
    ],
    "shapes/EllipseShape.java": [
        ('throw new IllegalArgumentException("缩放后的二次型不是椭圆")', 'throw new IllegalArgumentException(PlotI18n.error("error.plot.ellipse.validation.not_ellipse_after_scale"))'),
    ],
    "Matrix2d.java": [
        ('throw new IllegalStateException("矩阵不可逆")', 'throw new IllegalStateException(PlotI18n.error("error.plot.matrix.singular"))'),
    ],
}

IMPORT_LINE = "import com.plot.utils.PlotI18n;\n"


def ensure_import(content: str) -> str:
    if "import com.plot.utils.PlotI18n;" in content:
        return content
    marker = "import com.plot.core.model.Shape;"
    if marker in content:
        return content.replace(marker, marker + "\nimport com.plot.utils.PlotI18n;", 1)
    marker = "package com.plot.core.geometry;\n\n"
    if content.startswith(marker):
        return content.replace(marker, marker + IMPORT_LINE, 1)
    return IMPORT_LINE + content


def main() -> None:
    total = 0
    for rel, pairs in REPLACEMENTS.items():
        path = SRC / rel
        text = path.read_text(encoding="utf-8")
        count = 0
        for old, new in pairs:
            if old in text:
                text = text.replace(old, new)
                count += 1
            else:
                print(f"WARN missing in {rel}: {old[:60]}...")
        if count:
            text = ensure_import(text)
            path.write_text(text, encoding="utf-8")
            print(f"{rel}: {count} replacements")
            total += count
    print(f"Done. Total: {total}")


if __name__ == "__main__":
    main()
