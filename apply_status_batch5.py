#!/usr/bin/env python3
"""Merge batch-5 lang keys: handler validation, modify enums, array config, spline modes."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    # handler validation
    "status.plot.move.invalid_points": (
        "Move start or end point is invalid",
        "移动起点或终点无效",
    ),
    "status.plot.rotate.invalid_center": (
        "Rotation center point is invalid",
        "旋转中心点无效",
    ),
    "status.plot.rotate.angle_too_small": (
        "Rotation angle too small",
        "旋转角度太小",
    ),
    "status.plot.rotate.wrong_param_type": (
        "Invalid parameter type; expected ModifyParameters",
        "参数类型不正确，期望 ModifyParameters",
    ),
    "status.plot.scale.wrong_param_type": (
        "Invalid parameter type",
        "参数类型错误",
    ),
    "status.plot.scale.missing_center": (
        "Missing center point parameter",
        "缺少中心点参数",
    ),
    "status.plot.scale.factor_out_of_range": (
        "Scale factor out of valid range",
        "缩放因子超出有效范围",
    ),
    "status.plot.scale.ratio_out_of_range": (
        "Scale ratio out of valid range",
        "缩放系数超出有效范围",
    ),
    "status.plot.mirror.missing_axis_start": (
        "Mirror parameters invalid: missing axis start or center point",
        "镜像参数无效：缺少轴起点/中心点",
    ),
    "status.plot.mirror.invalid_axis_end": (
        "Mirror axis end point is invalid",
        "镜像轴终点无效",
    ),
    "status.plot.mirror.axis_too_short": (
        "Mirror axis is too short",
        "镜像轴长度太短",
    ),
    "status.plot.offset.no_selection": (
        "No shapes selected to offset",
        "没有选择要偏移的图形",
    ),
    "status.plot.offset.need_distance_or_point": (
        "Provide offset distance or offset point",
        "请提供偏移距离或偏移点",
    ),
    "status.plot.offset.distance_too_small": (
        "Offset distance too small",
        "偏移距离太小",
    ),
    "status.plot.offset.distance_too_large": (
        "Offset distance too large",
        "偏移距离太大",
    ),
    "status.plot.trim.no_selection": (
        "No shapes selected to trim",
        "没有选择要修剪的图形",
    ),
    "status.plot.trim.invalid_point": (
        "Trim point is invalid",
        "修剪点无效",
    ),
    "status.plot.trim.no_boundary": (
        "No boundary shapes found",
        "没有找到边界图形",
    ),
    "status.plot.break.missing_target": (
        "Missing target shape",
        "缺少目标图形",
    ),
    "status.plot.break.missing_first_point": (
        "Missing first break point",
        "缺少第一个打断点",
    ),
    "status.plot.break.missing_mode": (
        "Missing break mode",
        "缺少打断模式",
    ),
    "status.plot.extend.no_selection": (
        "No shapes selected to extend",
        "没有选择要延伸的图形",
    ),
    "status.plot.extend.wrong_param_type": (
        "Invalid parameter type; ExtendParameters required",
        "参数类型错误，需要ExtendParameters",
    ),
    "status.plot.array.wrong_param_type": (
        "Invalid parameter type; ModifyParameters expected",
        "参数类型不正确，期望 ModifyParameters",
    ),
    "status.plot.modify.missing_required_param": (
        "Missing required parameter: %s",
        "缺少必需参数: %s",
    ),
    "status.plot.modify.unsupported_type": (
        "Unsupported modify type: %s",
        "不支持的修改类型: %s",
    ),
    "status.plot.modify.validation_exception": (
        "Validation error: %s",
        "验证异常: %s",
    ),
    "status.plot.modify.operation_exception": (
        "Modify operation error: %s",
        "修改操作异常: %s",
    ),
    "status.plot.modify.operation_complete": (
        "Modify operation complete",
        "修改操作完成",
    ),
    "status.plot.modify.operation_failed": (
        "Modify operation failed: %s",
        "修改操作失败: %s",
    ),
    # ModifyType display names
    "modify.plot.type.move": ("Move", "移动"),
    "modify.plot.type.move.desc": ("Translate shape position", "平移图形位置"),
    "modify.plot.type.rotate": ("Rotate", "旋转"),
    "modify.plot.type.rotate.desc": ("Rotate shapes around a center point", "围绕中心点旋转图形"),
    "modify.plot.type.scale": ("Scale", "缩放"),
    "modify.plot.type.scale.desc": ("Change shape size", "改变图形大小"),
    "modify.plot.type.mirror": ("Mirror", "镜像"),
    "modify.plot.type.mirror.desc": ("Mirror shapes across an axis", "沿轴线镜像图形"),
    "modify.plot.type.transform": ("Transform", "变换"),
    "modify.plot.type.transform.desc": ("Apply combined transform operations", "对图形进行综合变换操作"),
    "modify.plot.type.offset": ("Offset", "偏移"),
    "modify.plot.type.offset.desc": ("Create offset copies", "创建偏移副本"),
    "modify.plot.type.array": ("Array", "阵列"),
    "modify.plot.type.array.desc": ("Create regularly spaced copies", "创建规律排列的副本"),
    "modify.plot.type.trim": ("Trim", "修剪"),
    "modify.plot.type.trim.desc": ("Trim part of a shape", "裁剪图形的一部分"),
    "modify.plot.type.extend": ("Extend", "延伸"),
    "modify.plot.type.extend.desc": ("Extend shape to a boundary", "延长图形到边界"),
    "modify.plot.type.fillet": ("Fillet", "圆角"),
    "modify.plot.type.fillet.desc": ("Create fillet at intersection", "在交点创建圆角"),
    "modify.plot.type.chamfer": ("Chamfer", "倒角"),
    "modify.plot.type.chamfer.desc": ("Create chamfer at intersection", "在交点创建斜面"),
    "modify.plot.type.stretch": ("Stretch", "拉伸"),
    "modify.plot.type.stretch.desc": ("Stretch selected shapes", "拉伸选中的图形"),
    "modify.plot.type.break": ("Break", "打断"),
    "modify.plot.type.break.desc": ("Break shape at specified point", "在指定点打断图形"),
    "modify.plot.type.boolean": ("Boolean", "布尔运算"),
    "modify.plot.type.boolean.desc": ("Perform boolean operations", "执行布尔运算操作"),
    # InteractionResult
    "mode.plot.interaction.continue": ("Continue", "继续"),
    "mode.plot.interaction.continue.desc": (
        "Interaction in progress; waiting for more input",
        "交互正在进行中，等待更多用户输入",
    ),
    "mode.plot.interaction.complete": ("Complete", "完成"),
    "mode.plot.interaction.complete.desc": (
        "Interaction finished; ready to commit shape",
        "交互已完成，可以提交最终图形",
    ),
    "mode.plot.interaction.cancel": ("Cancel", "取消"),
    "mode.plot.interaction.cancel.desc": (
        "Interaction cancelled; reset to initial state",
        "交互被取消，需要重置到初始状态",
    ),
    "mode.plot.interaction.ignored": ("Ignored", "忽略"),
    "mode.plot.interaction.ignored.desc": (
        "Event ignored for current strategy state",
        "当前事件不适用于此策略状态",
    ),
    "mode.plot.interaction.multi_step": ("Multi-Step Mode", "多步骤模式"),
    # spline short labels (referenced by SplineTool)
    "mode.plot.spline.fit_short": ("Fit", "拟合"),
    "mode.plot.spline.control_short": ("Control", "控制"),
    "mode.plot.spline.fit.desc_short": (
        "Curve passes through all points",
        "曲线通过所有点",
    ),
    "mode.plot.spline.control.desc_short": (
        "Points form the control polygon",
        "点作为控制多边形",
    ),
    # array config keys
    "config.plot.array.type": ("Array Type", "阵列类型"),
    "config.plot.array.row_count": ("Row Count", "行数"),
    "config.plot.array.column_count": ("Column Count", "列数"),
    "config.plot.array.row_spacing": ("Row Spacing", "行间距"),
    "config.plot.array.column_spacing": ("Column Spacing", "列间距"),
    "config.plot.array.radius": ("Radius", "半径"),
    "config.plot.array.angle_step": ("Angle Step", "角度步长"),
    "config.plot.array.path_points": ("Path Points", "路径点"),
    "config.plot.array.begin_pick_path": ("Begin Pick Path", "开始拾取路径"),
    "config.plot.array.begin_pick_objects": ("Begin Pick Objects", "开始拾取物件"),
    "config.plot.array.confirm": ("Confirm", "确认操作"),
}

for name in ("en_us.json", "zh_cn.json"):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, (en, zh) in KEYS.items():
        data[k] = en if name.startswith("en") else zh
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Merged {len(KEYS)} batch-5 keys")
