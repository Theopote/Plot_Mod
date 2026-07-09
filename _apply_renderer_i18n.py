import re
from pathlib import Path

BASE = Path('src/main/java/com/plot')
IMPORT = 'import com.plot.utils.PlotI18n;'

def ensure_import(content):
    if 'import com.plot.utils.PlotI18n' in content:
        return content
    m = re.search(r'(package [^;]+;\n\n)', content)
    if m:
        return content[:m.end()] + IMPORT + '\n' + content[m.end():]
    m = re.search(r'(package [^;]+;\n)', content)
    if m:
        return content[:m.end()] + '\n' + IMPORT + '\n' + content[m.end():]
    return content

def apply_replacements(path, replacements):
    p = BASE / path
    if not p.exists():
        print(f'MISSING: {path}')
        return False
    content = p.read_text(encoding='utf-8')
    original = content
    for old, new in replacements:
        content = content.replace(old, new)
    content = ensure_import(content)
    if content != original:
        p.write_text(content, encoding='utf-8')
        print(f'UPDATED: {path}')
        return True
    print(f'NOCHANGE: {path}')
    return False

draw_mode_files = [
    'ui/panel/tool/renderer/ArcToolOptionRenderer.java',
    'ui/panel/tool/renderer/EllipseToolOptionRenderer.java',
    'ui/panel/tool/renderer/CircleToolOptionRenderer.java',
    'ui/panel/tool/renderer/SplineToolOptionRenderer.java',
    'ui/panel/tool/renderer/RectangleToolOptionRenderer.java',
    'ui/panel/tool/renderer/CatenaryLineToolOptionRenderer.java',
    'ui/panel/tool/renderer/SemicircleToolOptionRenderer.java',
    'ui/panel/tool/renderer/PolylineToolOptionRenderer.java',
    'ui/panel/tool/renderer/PolygonToolOptionRenderer.java',
]
for f in draw_mode_files:
    apply_replacements(f, [('ImGui.text("绘制模式")', 'ImGui.text(PlotI18n.tr("option.plot.draw_mode"))')])

replacements_map = {
    'ui/panel/tool/renderer/FreeDrawToolOptionRenderer.java': [
        ('ImGui.text("平滑度")', 'ImGui.text(PlotI18n.tr("option.plot.smoothing"))'),
    ],
    'ui/panel/tool/renderer/SelectionToolOptionRenderer.java': [
        ('ImGui.text("选择模式")', 'ImGui.text(PlotI18n.tr("option.plot.selection_mode"))'),
    ],
    'ui/panel/tool/renderer/OffsetToolOptionRenderer.java': [
        ('ImGui.text("多重偏移")', 'ImGui.text(PlotI18n.tr("option.plot.multiple_offset"))'),
    ],
    'ui/panel/tool/renderer/EraserToolOptionRenderer.java': [
        ('ImGui.text("橡皮擦大小")', 'ImGui.text(PlotI18n.tr("option.plot.eraser_size"))'),
    ],
    'ui/panel/tool/renderer/MirrorToolOptionRenderer.java': [
        ('ImGui.text("对称模式")', 'ImGui.text(PlotI18n.tr("option.plot.symmetry_mode"))'),
    ],
    'ui/panel/tool/renderer/SineToolOptionRenderer.java': [
        ('ImGui.text("相位")', 'ImGui.text(PlotI18n.tr("option.plot.phase"))'),
    ],
    'ui/panel/tool/renderer/BreakToolOptionRenderer.java': [
        ('ImGui.text("打断模式")', 'ImGui.text(PlotI18n.tr("option.plot.break_mode"))'),
    ],
    'ui/panel/tool/renderer/FilletToolOptionRenderer.java': [
        ('ImGui.text("半径")', 'ImGui.text(PlotI18n.tr("option.plot.radius"))'),
    ],
    'ui/panel/tool/renderer/ChamferToolOptionRenderer.java': [
        ('ImGui.text("倒角距离")', 'ImGui.text(PlotI18n.tr("option.plot.chamfer_distance"))'),
    ],
    'ui/panel/tool/renderer/StarToolOptionRenderer.java': [
        ('ImGui.text("顶点数量")', 'ImGui.text(PlotI18n.tr("option.plot.vertex_count"))'),
        ('ImGui.text("内扭转")', 'ImGui.text(PlotI18n.tr("option.plot.inner_twist"))'),
        ('ImGui.text("外扭转")', 'ImGui.text(PlotI18n.tr("option.plot.outer_twist"))'),
    ],
    'ui/panel/tool/renderer/LineToolOptionRenderer.java': [
        ('ImGui.text("线型")', 'ImGui.text(PlotI18n.tr("option.plot.line_type"))'),
        ('ImGui.text("线条数量")', 'ImGui.text(PlotI18n.tr("option.plot.line_count"))'),
        ('ImGui.text("线条间距")', 'ImGui.text(PlotI18n.tr("option.plot.line_spacing"))'),
    ],
    'ui/panel/tool/renderer/RotateToolOptionRenderer.java': [
        ('ImGui.text("请选择旋转工具")', 'ImGui.text(PlotI18n.tr("option.plot.select_rotate_tool"))'),
        ('ImGui.text("角度步长")', 'ImGui.text(PlotI18n.tr("option.plot.angle_step"))'),
        ('ImGui.text("预设值")', 'ImGui.text(PlotI18n.tr("option.plot.preset_values"))'),
        ('ImGui.text("角度吸附")', 'ImGui.text(PlotI18n.tr("option.plot.angle_snap"))'),
    ],
    'ui/panel/tool/renderer/TransformToolOptionRenderer.java': [
        ('ImGui.text("请选择变换工具")', 'ImGui.text(PlotI18n.tr("option.plot.select_transform_tool"))'),
        ('ImGui.text("旋转功能")', 'ImGui.text(PlotI18n.tr("option.plot.rotation_feature"))'),
    ],
    'ui/panel/tool/renderer/AlignToolOptionRenderer.java': [
        ('ImGui.text("对齐模式(已简化)")', 'ImGui.text(PlotI18n.tr("option.plot.align_mode"))'),
        ('ImGui.text("参考模式(已简化)")', 'ImGui.text(PlotI18n.tr("option.plot.reference_mode"))'),
        ('ImGui.text("缩放设置")', 'ImGui.text(PlotI18n.tr("option.plot.scale_settings"))'),
        ('if (ImGui.checkbox("允许缩放", scaleEnabled))', 'if (ImGui.checkbox(PlotI18n.tr("option.plot.allow_scale"), scaleEnabled))'),
    ],
    'ui/panel/tool/renderer/ScaleToolOptionRenderer.java': [
        ('if (ImGui.collapsingHeader("使用说明", ImGuiTreeNodeFlags.DefaultOpen))', 'if (ImGui.collapsingHeader(PlotI18n.tr("option.plot.usage_instructions"), ImGuiTreeNodeFlags.DefaultOpen))'),
    ],
    'ui/panel/tool/renderer/AnnotationToolOptionRenderer.java': [
        ('ImGui.text("模式")', 'ImGui.text(PlotI18n.tr("option.plot.annotation_mode"))'),
        ('if (ImGui.button("距离", BUTTON_SIZE, BUTTON_SIZE))', 'if (ImGui.button(PlotI18n.tr("option.plot.annotation_distance"), BUTTON_SIZE, BUTTON_SIZE))'),
        ('if (ImGui.button("角度", BUTTON_SIZE, BUTTON_SIZE))', 'if (ImGui.button(PlotI18n.tr("option.plot.annotation_angle"), BUTTON_SIZE, BUTTON_SIZE))'),
        ('if (ImGui.button("半径", BUTTON_SIZE, BUTTON_SIZE))', 'if (ImGui.button(PlotI18n.tr("option.plot.annotation_radius"), BUTTON_SIZE, BUTTON_SIZE))'),
        ('if (ImGui.button("面积", BUTTON_SIZE, BUTTON_SIZE))', 'if (ImGui.button(PlotI18n.tr("option.plot.annotation_area"), BUTTON_SIZE, BUTTON_SIZE))'),
    ],
    'ui/panel/tool/renderer/SpiralToolOptionRenderer.java': [
        ('ImGui.text("尖角样式")', 'ImGui.text(PlotI18n.tr("option.plot.sharp_style"))'),
        ('ImGui.text("边数")', 'ImGui.text(PlotI18n.tr("option.plot.sides"))'),
        ('ImGui.text("逆时针")', 'ImGui.text(PlotI18n.tr("option.plot.clockwise"))'),
        ('ImGui.text("请选择螺旋线工具")', 'ImGui.text(PlotI18n.tr("option.plot.select_spiral_tool"))'),
        ('ImGui.text("螺旋类型")', 'ImGui.text(PlotI18n.tr("option.plot.spiral_type"))'),
        ('ImGui.text("当前状态")', 'ImGui.text(PlotI18n.tr("option.plot.current_state"))'),
    ],
    'ui/panel/tool/renderer/PolygonToolOptionRenderer.java': [
        ('ImGui.text("边数")', 'ImGui.text(PlotI18n.tr("option.plot.sides"))'),
    ],
    'ui/panel/tool/renderer/ArrayToolOptionRenderer.java': [
        ('ImGui.text("阵列类型")', 'ImGui.text(PlotI18n.tr("option.plot.array_type"))'),
        ('if (ImGui.button("矩形阵列", 80, 30))', 'if (ImGui.button(PlotI18n.tr("array.plot.rectangular"), 80, 30))'),
        ('if (ImGui.button("环形阵列", 80, 30))', 'if (ImGui.button(PlotI18n.tr("array.plot.polar"), 80, 30))'),
        ('if (ImGui.button("路径阵列", 80, 30))', 'if (ImGui.button(PlotI18n.tr("array.plot.path"), 80, 30))'),
        ('ImGui.text("行数")', 'ImGui.text(PlotI18n.tr("option.plot.row_count"))'),
        ('ImGui.text("列数")', 'ImGui.text(PlotI18n.tr("option.plot.column_count"))'),
        ('ImGui.text("行间距")', 'ImGui.text(PlotI18n.tr("option.plot.row_spacing"))'),
        ('ImGui.text("列间距")', 'ImGui.text(PlotI18n.tr("option.plot.column_spacing"))'),
        ('if (ImGui.button("完成", 80, 24))', 'if (ImGui.button(PlotI18n.tr("button.plot.done"), 80, 24))'),
        ('ImGui.text("数量")', 'ImGui.text(PlotI18n.tr("option.plot.count"))'),
        ('ImGui.text("半径")', 'ImGui.text(PlotI18n.tr("option.plot.radius"))'),
        ('ImGui.text("角度间隔")', 'ImGui.text(PlotI18n.tr("option.plot.angle_interval"))'),
        ('ImGui.text("点位数")', 'ImGui.text(PlotI18n.tr("option.plot.point_count"))'),
        ('ImGui.text("路径长度")', 'ImGui.text(PlotI18n.tr("option.plot.path_length"))'),
        ('ImGui.text("等距步长")', 'ImGui.text(PlotI18n.tr("option.plot.step_distance"))'),
        ('if (ImGui.button("完成", 90, 26))', 'if (ImGui.button(PlotI18n.tr("button.plot.done"), 90, 26))'),
    ],
    'ui/panel/tool/renderer/TrimToolOptionRenderer.java': [
        ('ImGui.text("当前状态")', 'ImGui.text(PlotI18n.tr("option.plot.current_state"))'),
        ('ImGui.text("选择模式")', 'ImGui.text(PlotI18n.tr("option.plot.selection_mode"))'),
        ('ImGui.text("修剪模式")', 'ImGui.text(PlotI18n.tr("option.plot.trim_mode"))'),
        ('ImGui.text("处理中")', 'ImGui.text(PlotI18n.tr("option.plot.processing"))'),
        ('ImGui.text("未知状态")', 'ImGui.text(PlotI18n.tr("option.plot.unknown_state"))'),
        ('if (ImGui.button("边界修剪", 80, 30))', 'if (ImGui.button(PlotI18n.tr("trim.plot.boundary"), 80, 30))'),
        ('if (ImGui.button("栅栏修剪", 80, 30))', 'if (ImGui.button(PlotI18n.tr("option.plot.fence_trim"), 80, 30))'),
        ('ImGui.text("栅栏类型")', 'ImGui.text(PlotI18n.tr("option.plot.fence_type"))'),
        ('ImGui.text("边数")', 'ImGui.text(PlotI18n.tr("option.plot.sides"))'),
        ('ImGui.text("修剪容差")', 'ImGui.text(PlotI18n.tr("option.plot.trim_tolerance"))'),
    ],
    'ui/panel/tool/renderer/TextToolOptionRenderer.java': [
        ('ImGui.text("说明")', 'ImGui.text(PlotI18n.tr("option.plot.text_description"))'),
        ('ImGui.text("字体大小")', 'ImGui.text(PlotI18n.tr("option.plot.font_size"))'),
        ('ImGui.text("样式")', 'ImGui.text(PlotI18n.tr("option.plot.style"))'),
        ('if (ImGui.checkbox("粗体##bold", tempBold))', 'if (ImGui.checkbox(PlotI18n.tr("option.plot.text_bold") + "##bold", tempBold))'),
        ('if (ImGui.checkbox("斜体##italic", tempItalic))', 'if (ImGui.checkbox(PlotI18n.tr("option.plot.text_italic") + "##italic", tempItalic))'),
        ('ImGui.text("对齐")', 'ImGui.text(PlotI18n.tr("option.plot.text_align"))'),
        ('ImGui.text("行高")', 'ImGui.text(PlotI18n.tr("option.plot.text_line_height"))'),
        ('ImGui.text("输入方式")', 'ImGui.text(PlotI18n.tr("option.plot.input_method"))'),
        ('if (ImGui.checkbox("使用对话框##use_dialog", tempUseDialog))', 'if (ImGui.checkbox(PlotI18n.tr("option.plot.text_use_dialog") + "##use_dialog", tempUseDialog))'),
        ('ImGui.text("操作")', 'ImGui.text(PlotI18n.tr("option.plot.text_operations"))'),
        ('if (ImGui.button("重置大小", 80, 20))', 'if (ImGui.button(PlotI18n.tr("button.plot.reset_size"), 80, 20))'),
        ('if (ImGui.button("转换图形", 80, 20))', 'if (ImGui.button(PlotI18n.tr("button.plot.convert_shape"), 80, 20))'),
    ],
}

for path, reps in replacements_map.items():
    apply_replacements(path, reps)

print('Done bulk replacements')
