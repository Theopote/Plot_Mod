#!/usr/bin/env python3
"""Apply i18n to tool tooltips, extend/scale panels, plugins, and extension panel."""
import json
import re
from pathlib import Path

ROOT = Path('.')
LANG_DIR = ROOT / 'src/main/resources/assets/plot/lang'
BASE = ROOT / 'src/main/java/com/plot'
IMPORT = 'import com.plot.utils.PlotI18n;'

NEW_KEYS = {
    # Common
    "status.plot.error": ("Error", "错误"),
    "panel.plot.extension_disabled": ("Plugin Disabled", "插件未启用"),
    "panel.plot.extension_render_error": ("Render error: %s", "渲染错误: %s"),
    "panel.plot.extension_select_plugin": ("Select a plugin", "请选择一个插件"),
    "panel.plot.extension_select_hint": (
        "Choose a plugin from the list above to view and configure its settings.",
        "从上方列表中选择一个插件以查看和配置其参数",
    ),

    # Plugin metadata
    "plugin.image_tools.name": ("Image Tools", "图片工具"),
    "plugin.image_tools.desc": ("Import and edit images", "用于导入和编辑图片"),
    "plugin.earthwork_balance.name": ("Earthwork Balance", "土方平衡"),
    "plugin.earthwork_balance.desc": (
        "Calculate and optimize cut/fill plans for site grading",
        "计算和优化场地平整时的土方挖填方案",
    ),
    "plugin.road_system.name": ("Road System", "道路系统"),
    "plugin.road_system.desc": ("Plan and build various types of roads", "用于规划和建造各种类型的道路"),

    # Image plugin
    "plugin.image.import": ("Import Image", "导入图片"),
    "plugin.image.crop": ("Crop", "裁剪"),
    "plugin.image.flip": ("Flip", "翻转"),
    "plugin.image.stretch": ("Stretch", "拉伸"),
    "plugin.image.brightness": ("Brightness: %.0f%%", "亮度: %.0f%%"),
    "plugin.image.contrast": ("Contrast: %.0f%%", "对比度: %.0f%%"),
    "plugin.image.scale": ("Scale: %.0f%%", "缩放: %.0f%%"),
    "plugin.image.rotation": ("Rotation: %.0f°", "旋转: %.0f°"),
    "plugin.image.auto_enhance": ("Auto Enhance", "自动增强"),
    "plugin.image.fit_canvas": ("Fit Canvas", "适应画布"),

    # Earthwork plugin tools
    "plugin.earthwork.cut_area": ("Cut Area", "挖方区域"),
    "plugin.earthwork.fill_area": ("Fill Area", "填方区域"),
    "plugin.earthwork.level_site": ("Level Site", "场地整平"),
    "plugin.earthwork.elevation_measure": ("Measure Elevation", "高程测量"),
    "plugin.earthwork.volume_calc": ("Volume Calculation", "方量计算"),
    "plugin.earthwork.export_report": ("Export Report", "导出报告"),
    "plugin.earthwork.unit_cubic_meter": ("%.2f m³", "%.2f m³"),

    # Road plugin extras
    "plugin.road.path_selected": ("Selected path: %.1f m", "已选择路径: %.1f 米"),
    "plugin.road.path_type": ("Path type: %s", "路径类型: %s"),
    "plugin.road.paths_found": ("Found %d available paths", "找到 %d 个可用路径"),
    "plugin.road.no_path_found": ("No path found", "未找到路径"),
    "plugin.road.select_path_combo": ("Select path...", "选择路径..."),
    "plugin.road.path_combo_item": ("%s (%.1fm)", "%s (%.1fm)"),
    "plugin.road.max_slope": ("Max slope: %.1f%%", "最大坡度: %.1f%%"),
    "plugin.road.preview_first": ("Draw a path and calculate preview first", "请先绘制路径并计算预览"),
    "plugin.road.balance_percent": ("Balance: %.1f%%", "平衡度: %.1f%%"),
    "plugin.road.blocks_count": ("%d blocks", "%d 方块"),
    "plugin.road.bridges_count": ("%d bridges", "%d 座"),
    "plugin.road.tunnels_count": ("%d tunnels", "%d 段"),
    "material.plot.concrete": ("Concrete", "混凝土"),
    "material.plot.stone": ("Stone", "石头"),
    "material.plot.gravel": ("Gravel", "砂砾"),
    "material.plot.planks": ("Wood Planks", "木板"),
    "path.plot.polyline": ("Polyline", "折线"),
    "path.plot.freedraw": ("Free Draw", "自由绘制"),
    "path.plot.spline": ("Spline", "样条曲线"),
    "path.plot.bezier": ("Bezier Curve", "贝塞尔曲线"),
    "path.plot.unknown": ("Unknown", "未知"),

    # Align tool
    "hint.plot.align.no_mode_needed": (
        "Align via point-to-point interaction; no mode selection needed.",
        "通过点对点交互完成对齐，无需选择模式",
    ),
    "hint.plot.align.select_mode": ("Choose an align mode; shortcuts switch quickly.", "选择对齐方式，快捷键可直接切换"),
    "hint.plot.align.no_reference_needed": (
        "Uses point-to-point reference; no reference mode selection needed.",
        "以点对点选择为参考，无需选择参考模式",
    ),
    "hint.plot.align.select_reference": ("Choose the align reference object.", "选择对齐的参考对象"),
    "hint.plot.align.scale_on_align": (
        "When enabled, scales while aligning to match target size precisely.",
        "开启后将对齐时进行缩放以精确匹配目标尺寸",
    ),
    "align.plot.mode.left": ("Align Left (L)", "左对齐 (L)"),
    "align.plot.mode.right": ("Align Right (R)", "右对齐 (R)"),
    "align.plot.mode.center": ("Align Center (C)", "中心对齐 (C)"),
    "align.plot.mode.top": ("Align Top (T)", "顶部对齐 (T)"),
    "align.plot.mode.bottom": ("Align Bottom (B)", "底部对齐 (B)"),
    "align.plot.mode.middle": ("Align Middle (M)", "中间对齐 (M)"),
    "align.plot.mode.distribute_h": ("Distribute Horizontally", "水平分布"),
    "align.plot.mode.distribute_v": ("Distribute Vertically", "垂直分布"),
    "align.plot.ref.selection_bounds": ("Selection Bounds", "选择边界"),
    "align.plot.ref.first_selected": ("First Selected", "第一个选中"),
    "align.plot.ref.last_selected": ("Last Selected", "最后选中"),
    "align.plot.ref.largest": ("Largest Shape", "最大图形"),

    # Annotation
    "hint.plot.annotation.distance_icon": ("Distance annotation in blocks", "距离标注：以方块为单位"),
    "hint.plot.annotation.angle_icon": ("Angle annotation between two line segments", "角度标注：两条线段之间的夹角"),
    "hint.plot.annotation.radius_icon": ("Radius annotation for circles, semicircles, and arcs", "半径标注：标注圆形、半圆、圆弧的半径"),
    "hint.plot.annotation.area_icon": ("Area annotation: block count in region", "区域标注：区域内的方块数量"),
    "hint.plot.annotation.distance_btn": ("Annotate distance between two points (in blocks)", "标注两点之间的距离（以方块为单位）"),
    "hint.plot.annotation.angle_btn": ("Select two angled lines; angle is annotated when selection completes", "选中两条有夹角的直线，右键完成选中时自动标注角度"),
    "hint.plot.annotation.radius_btn": ("Select circles, semicircles, or arcs to annotate radius", "点选或框选圆形、半圆、圆弧图形，右键完成选中时自动标注半径"),
    "hint.plot.annotation.area_btn": ("Annotate block count in a region", "标注区域内的方块数量"),

    # Array
    "hint.plot.array.rectangular": ("Rectangular array: arrange copies in rows and columns", "矩形阵列：按行列排列图形"),
    "hint.plot.array.polar": ("Polar array: arrange copies in a circle", "环形阵列：按圆形排列图形"),
    "hint.plot.array.path": ("Path array: evenly distribute along path (count includes endpoints)", "路径阵列：沿路径等距分配（数量=点位数，含起点与终点）"),
    "array.plot.rectangular_params": ("Rectangular Array Parameters", "矩形阵列参数"),
    "array.plot.polar_params": ("Polar Array Parameters", "环形阵列参数"),
    "hint.plot.array.select_source_first": ("Select source shape and set base point first", "请先选择源图形和设置基准点"),
    "hint.plot.array.confirm_rectangular": ("Confirm rectangular array creation", "确认创建矩形阵列"),
    "hint.plot.array.confirm_polar": ("Confirm polar array creation", "确认创建环形阵列"),
    "hint.plot.array.confirm_path": ("Confirm path array creation", "确认创建路径阵列"),
    "hint.plot.array.select_source_and_path": ("Select source shape and pick a path", "请先选择源图形和拾取路径"),
    "hint.plot.array.path_instructions": (
        "Right-click to confirm selection, then left-click a path on canvas. Count is evenly spaced points including endpoints.",
        "操作：右键确认选中后，在画布左键点击一条路径；数量表示路径上的等距点位数（含起点与终点）",
    ),
    "hint.plot.array.no_path_picked": ("No path picked", "未拾取路径"),

    # Trim
    "hint.plot.trim.boundary": ("Boundary trim: select boundary, then click the side to trim", "边界修剪：选择边界图形，然后点击要修剪的图形一侧"),
    "hint.plot.trim.fence": ("Fence trim: define a fence line for batch trimming", "栅栏修剪：定义栅栏线进行批量修剪"),
    "hint.plot.trim.fence_type": ("Set fence trim boundary type", "设置栅栏修剪边界类型"),
    "hint.plot.trim.fence_sides": ("Set regular polygon fence side count", "设置正多边形栅栏的边数"),
    "trim.plot.params": ("Trim Parameters", "修剪参数"),
    "hint.plot.trim.tolerance": ("Trim tolerance affects trim point detection precision", "修剪操作的容差范围，影响修剪点的检测精度"),
    "trim.plot.item.boundary": ("boundary shape", "边界图形"),
    "trim.plot.item.target": ("target shape", "目标图形"),
    "trim.plot.item.shape": ("shape", "图形"),
    "trim.plot.selected_count": ("Selected %d %s", "已选择 %d 个%s"),
    "trim.plot.fence_points": ("Fence points: %d", "栅栏点数: %d"),
    "fence.plot.polyline": ("Polyline", "Polyline"),
    "fence.plot.rectangle": ("Rectangle", "矩形"),
    "fence.plot.circle": ("Circle", "圆形"),
    "fence.plot.ellipse": ("Ellipse", "椭圆"),
    "fence.plot.polygon": ("Regular Polygon", "正多边形"),

    # Break
    "hint.plot.break.single": ("Single-point break: click on a shape outline to split it", "单点打断：在任何图形的轮廓上点击，图形会从点击位置一分为二"),
    "hint.plot.break.two_point": ("Two-point break: click two points on a line to remove the segment between them", "两点打断：在同一条线上点击两点，删除两点之间的图形部分"),

    # Mirror
    "hint.plot.mirror.axis": ("Axis mirror: mirror across a line defined by two points", "轴对称：关于一条轴线做对称（两点定义轴）"),
    "hint.plot.mirror.center": ("Center mirror: mirror about a center point (180° rotation)", "中心对称：关于一个中心点做对称（等价于绕该点旋转180°）"),

    # Line
    "hint.plot.line.single": ("Single line mode", "单线模式"),
    "hint.plot.line.multi": ("Multi-line mode", "多线模式"),

    # Offset
    "hint.plot.offset.multiple": ("When enabled, offset multiple objects continuously", "启用后可以连续偏移多个对象"),

    # Rotate
    "hint.plot.rotate.angle_step": ("Angle snap step when holding Shift (1°–90°)\nDouble-click to enter a value", "按住Shift键时的角度对齐步长（1°-90°）\n双击可手动输入数值"),
    "hint.plot.rotate.angle_snap": ("When enabled, rotation snaps to the configured angle step", "启用后旋转时会自动吸附到指定角度"),
    "hint.plot.rotate.preset_step": ("Set angle step to %s°", "设置为 %s° 角度步长"),

    # Transform
    "hint.plot.transform.rotation_drag": ("When enabled, drag outside corners to rotate", "启用后，可以在角点外侧拖拽进行旋转"),

    # Selection
    "hint.plot.select.normal": ("Normal selection mode", "普通选择模式"),
    "hint.plot.select.lasso": ("Lasso selection mode", "套索选择模式"),

    # Catenary
    "hint.plot.catenary.standard": ("Standard mode: symmetric curve; third point controls sag depth", "标准模式：曲线对称，第三点控制弧垂深度"),
    "hint.plot.catenary.spline": ("Spline mode: asymmetric curve; third point is a free control point", "样条插值模式：非对称曲线，第三点为控制点，可拖拽至任意位置"),

    # Chamfer / Fillet errors
    "hint.plot.chamfer.distance": ("Set chamfer distance", "设置倒角斜面的距离大小"),
    "error.plot.chamfer_render": ("Error rendering chamfer tool options", "渲染倒角工具选项时发生错误"),
    "error.plot.fillet_render": ("Error rendering fillet tool options", "渲染圆角工具选项时发生错误"),

    # Eraser
    "hint.plot.eraser.size": ("Adjust eraser size; larger values erase a wider area", "调整橡皮擦大小，数值越大擦除范围越大"),

    # Text tool
    "hint.plot.text.moved_to_dialog": (
        "Style, font size, and alignment are configured in the text input dialog.",
        "样式、字号、对齐已移动到文字输入对话框中设置",
    ),
    "hint.plot.text.dialog_input": ("Open a dialog to enter text when clicking", "点击时弹出对话框输入文字"),
    "hint.plot.text.reset_font": ("Reset font size to default", "将字体大小重置为默认值"),
    "hint.plot.text.convert_shape": ("Convert selected text to shapes", "将选中的文字转换为图形"),
    "hint.plot.text.decrease": ("Decrease quickly", "快速减小值"),
    "hint.plot.text.increase": ("Increase quickly", "快速增大值"),

    # Scale
    "hint.plot.scale.usage_title": ("Scale tool steps:", "缩放工具使用步骤："),
    "hint.plot.scale.workflow": ("Workflow:", "交互流程："),
    "hint.plot.scale.step1": ("1. Select shapes to scale with the select tool", "1. 使用选择工具选择要缩放的图形"),
    "hint.plot.scale.step2": ("2. Switch to the scale tool", "2. 切换到缩放工具"),
    "hint.plot.scale.step3": ("1. Select shapes with the select tool", "1. 使用选择工具选择图形"),
    "hint.plot.scale.step4": ("2. Switch to scale tool; first click or selection center sets the center", "2. 切换到缩放工具，第一次点击或以选择框中心确定中心点"),
    "hint.plot.scale.step5": ("3. Second click or drag sets reference and scale; click to finish", "3. 第二次点击或拖动设置参考点并缩放，点击完成"),

    # Extend
    "hint.plot.extend.boundaries_selected": ("Selected %d boundary shapes", "已选择 %d 个边界图形"),
    "hint.plot.extend.status": ("Status: %s", "状态: %s"),
    "hint.plot.extend.mode_auto": ("Mode: Auto Extend", "模式: 自动延伸"),
    "hint.plot.extend.hint": ("Hint: %s", "提示: %s"),
    "hint.plot.extend.continuous_active": ("Extend mode active; extend multiple shapes continuously", "✓ 延伸模式已激活，可连续延伸多个图形"),
    "hint.plot.extend.no_boundary": ("No boundary shape selected", "未选择边界图形"),
    "hint.plot.extend.select_boundary_first": ("Select boundary shapes first, then right-click to confirm", "提示: 请先选择边界图形，然后右键确认"),
    "hint.plot.extend.auto_mode_title": ("Auto Extend Mode", "自动延伸模式"),
    "hint.plot.extend.auto_mode_desc": (
        "The tool picks the best extend method automatically: standard extend first, then projection if no intersection.",
        "工具会自动选择最佳延伸方式：先尝试标准延伸，如果没有交点则自动使用投影延伸。",
    ),
    "hint.plot.extend.select_boundary_hint": ("Select boundary shapes (click or box-select)", "请选择边界图形，支持点选和框选"),
    "hint.plot.extend.boundary_confirmed": ("Selected %d boundary shapes; right-click to confirm", "已选择 %d 个边界图形，右键确认边界选择"),
    "hint.plot.extend.extending": ("Extend mode active; click endpoints to extend (boundaries: %d)", "延伸模式已激活，点击要延伸的图形端点执行延伸（当前边界数: %d）"),
    "hint.plot.extend.follow_prompt": ("Follow the on-screen prompts", "请按照提示进行操作"),

    # Polygon
    "mode.plot.polygon.center_vertex_short": ("Center-Vertex Mode", "中心-顶点模式"),
    "mode.plot.polygon.center_radius_short": ("Center-Radius Mode", "中心-半径模式"),

    # Spiral
    "option.plot.growth_factor": ("Growth Factor", "生长因子"),
    "option.plot.expansion_rate": ("Expansion Rate", "扩张率"),
    "option.plot.spiral_coefficient": ("Spiral Coefficient", "螺旋系数"),
    "option.plot.pitch": ("Pitch", "螺距"),
    "hint.plot.spiral.center_set": ("✓ Center point set", "✓ 已确定中心点"),
    "hint.plot.spiral.start_set": ("✓ Spiral start set", "✓ 已确定螺旋起点"),
    "hint.plot.spiral.pitch_set": ("✓ Pitch set", "✓ 已确定螺距"),
    "hint.plot.spiral.outer_set": ("✓ Outer ring set", "✓ 已确定最外圈"),
}

def merge_lang():
    en = json.loads((LANG_DIR / 'en_us.json').read_text(encoding='utf-8'))
    zh = json.loads((LANG_DIR / 'zh_cn.json').read_text(encoding='utf-8'))
    for key, (en_val, zh_val) in NEW_KEYS.items():
        en[key] = en_val
        zh[key] = zh_val
    (LANG_DIR / 'en_us.json').write_text(json.dumps(en, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (LANG_DIR / 'zh_cn.json').write_text(json.dumps(zh, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Merged {len(NEW_KEYS)} keys; total {len(en)} keys')

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

def patch(path, replacements):
    p = BASE / path if not str(path).startswith('src') else ROOT / path
    if not p.exists():
        print(f'MISSING {path}')
        return
    text = p.read_text(encoding='utf-8')
    orig = text
    for old, new in replacements:
        if old not in text:
            continue
        text = text.replace(old, new)
    text = ensure_import(text)
    if text != orig:
        p.write_text(text, encoding='utf-8')
        print(f'UPDATED {path}')

# --- Extension panel ---
patch('com/plot/ui/panel/extension/ExtensionPanel.java', [
    ('ImGui.textColored(theme.errorText, "插件未启用")', 'ImGui.textColored(theme.errorText, PlotI18n.tr("panel.plot.extension_disabled"))'),
    ('ImGui.textColored(theme.errorText, "渲染错误: " + e.getMessage())', 'ImGui.textColored(theme.errorText, PlotI18n.tr("panel.plot.extension_render_error", e.getMessage()))'),
    ('ImGui.textColored(theme.mutedText, "请选择一个插件")', 'ImGui.textColored(theme.mutedText, PlotI18n.tr("panel.plot.extension_select_plugin"))'),
    ('ImGui.textWrapped("从上方列表中选择一个插件以查看和配置其参数")', 'ImGui.textWrapped(PlotI18n.tr("panel.plot.extension_select_hint"))'),
])

# --- Plugin base: resolve i18n in getters ---
plugin_java = (BASE / 'plugin/Plugin.java').read_text(encoding='utf-8')
if 'PlotI18n' not in plugin_java:
    plugin_java = plugin_java.replace(
        'import com.plot.core.plugin.PluginConfig;',
        'import com.plot.core.plugin.PluginConfig;\nimport com.plot.utils.PlotI18n;'
    )
plugin_java = plugin_java.replace(
    '''    @Override
    public String getName() {
        return name;
    }''',
    '''    @Override
    public String getName() {
        return name.startsWith("plugin.") ? PlotI18n.tr(name) : name;
    }'''
)
plugin_java = plugin_java.replace(
    '''    @Override
    public String getDescription() {
        return description;
    }''',
    '''    @Override
    public String getDescription() {
        return description.startsWith("plugin.") ? PlotI18n.tr(description) : description;
    }'''
)
(BASE / 'plugin/Plugin.java').write_text(plugin_java, encoding='utf-8')
print('UPDATED plugin/Plugin.java')

patch('com/plot/plugin/ImageToolsPlugin.java', [
    ('"图片工具",\n            "用于导入和编辑图片"', '"plugin.image_tools.name",\n            "plugin.image_tools.desc"'),
    ('ImGui.text("图片操作")', 'ImGui.text(PlotI18n.tr("plugin.image.image_operations"))'),
    ('        String[][] operations = {\n            {Icons.IMAGE_PLUS, "导入图片"},\n            {Icons.CROP, "裁剪"},\n            {Icons.FLIP_HORIZONTAL, "翻转"},\n            {Icons.ARROWS_MAXIMIZE, "拉伸"}\n        };\n        int columns = 2;\n        ImGui.columns(columns, "operations_columns", false);\n        for (String[] op : operations) {\n            if (UIUtils.iconButton(op[0], op[1], false)) {\n                // 执行相应操作\n            }\n            ImGui.nextColumn();\n        }',
     '        String[] opIcons = {Icons.IMAGE_PLUS, Icons.CROP, Icons.FLIP_HORIZONTAL, Icons.ARROWS_MAXIMIZE};\n        String[] opLabels = {\n            PlotI18n.tr("plugin.image.import"),\n            PlotI18n.tr("plugin.image.crop"),\n            PlotI18n.tr("plugin.image.flip"),\n            PlotI18n.tr("plugin.image.stretch")\n        };\n        int columns = 2;\n        ImGui.columns(columns, "operations_columns", false);\n        for (int oi = 0; oi < opIcons.length; oi++) {\n            if (UIUtils.iconButton(opIcons[oi], opLabels[oi], false)) {\n                // 执行相应操作\n            }\n            ImGui.nextColumn();\n        }'),
    ('ImGui.text(String.format("亮度: %.0f%%", config.getBrightness()))', 'ImGui.text(String.format(PlotI18n.tr("plugin.image.brightness"), config.getBrightness()))'),
    ('ImGui.text(String.format("对比度: %.0f%%", config.getContrast()))', 'ImGui.text(String.format(PlotI18n.tr("plugin.image.contrast"), config.getContrast()))'),
    ('ImGui.text("保持宽高比")', 'ImGui.text(PlotI18n.tr("plugin.image.aspect_ratio"))'),
    ('ImGui.text(String.format("缩放: %.0f%%", config.getScale()))', 'ImGui.text(String.format(PlotI18n.tr("plugin.image.scale"), config.getScale()))'),
    ('ImGui.text(String.format("旋转: %.0f°", config.getRotation()))', 'ImGui.text(String.format(PlotI18n.tr("plugin.image.rotation"), config.getRotation()))'),
    ('ImGui.text("图片预设")', 'ImGui.text(PlotI18n.tr("plugin.image.image_presets"))'),
    ('ImGui.text("工具")', 'ImGui.text(PlotI18n.tr("plugin.image.tools"))'),
    ('UIUtils.iconButton(Icons.CONTRAST, "自动增强", false)', 'UIUtils.iconButton(Icons.CONTRAST, PlotI18n.tr("plugin.image.auto_enhance"), false)'),
    ('UIUtils.iconButton(Icons.MAXIMIZE, "适应画布", false)', 'UIUtils.iconButton(Icons.MAXIMIZE, PlotI18n.tr("plugin.image.fit_canvas"), false)'),
    ('ImGui.text("最近使用")', 'ImGui.text(PlotI18n.tr("plugin.image.recently_used"))'),
])

patch('com/plot/plugin/EarthworkPlugin.java', [
    ('"土方平衡",\n            "计算和优化场地平整时的土方挖填方案"', '"plugin.earthwork_balance.name",\n            "plugin.earthwork_balance.desc"'),
    ('ImGui.text("网格设置")', 'ImGui.text(PlotI18n.tr("plugin.earthwork.grid_settings"))'),
    ('ImGui.text("网格大小: " + config.getGridSize() + "米")', 'ImGui.text(PlotI18n.tr("plugin.earthwork.grid_size", config.getGridSize()))'),
    ('if (ImGui.checkbox("显示网格", showGridRef))', 'if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.show_grid"), showGridRef))'),
    ('ImGui.text("计算设置")', 'ImGui.text(PlotI18n.tr("plugin.earthwork.calc_settings"))'),
    ('if (ImGui.checkbox("自动平衡", autoBalanceRef))', 'if (ImGui.checkbox(PlotI18n.tr("plugin.earthwork.auto_balance"), autoBalanceRef))'),
    ('ImGui.text("目标高程: " + config.getTargetElevation() + "米")', 'ImGui.text(PlotI18n.tr("plugin.earthwork.target_elevation", config.getTargetElevation()))'),
    ('ImGui.text("填方系数: " + config.getFillFactor())', 'ImGui.text(PlotI18n.tr("plugin.earthwork.fill_factor", config.getFillFactor()))'),
    ('ImGui.text("土方统计")', 'ImGui.text(PlotI18n.tr("plugin.earthwork.earthwork_stats"))'),
    ('ImGui.text("挖方量:")', 'ImGui.text(PlotI18n.tr("plugin.earthwork.cut_volume"))'),
    ('ImGui.text(String.format("%.2f m³", config.getCutVolume()))', 'ImGui.text(String.format(PlotI18n.tr("plugin.earthwork.unit_cubic_meter"), config.getCutVolume()))'),
    ('ImGui.text("填方量:")', 'ImGui.text(PlotI18n.tr("plugin.earthwork.fill_volume"))'),
    ('ImGui.text(String.format("%.2f m³", config.getFillVolume()))', 'ImGui.text(String.format(PlotI18n.tr("plugin.earthwork.unit_cubic_meter"), config.getFillVolume()))'),
    ('ImGui.text("净方量:")', 'ImGui.text(PlotI18n.tr("plugin.earthwork.net_volume"))'),
    ('String.format("%.2f m³", Math.abs(netVolume))', 'String.format(PlotI18n.tr("plugin.earthwork.unit_cubic_meter"), Math.abs(netVolume))'),
    ('ImGui.text("工具")', 'ImGui.text(PlotI18n.tr("plugin.earthwork.tools"))'),
    ('UIUtils.iconButton(Icons.TERRAIN_CUT, "挖方区域", false)', 'UIUtils.iconButton(Icons.TERRAIN_CUT, PlotI18n.tr("plugin.earthwork.cut_area"), false)'),
    ('UIUtils.iconButton(Icons.TERRAIN_FILL, "填方区域", false)', 'UIUtils.iconButton(Icons.TERRAIN_FILL, PlotI18n.tr("plugin.earthwork.fill_area"), false)'),
    ('UIUtils.iconButton(Icons.TERRAIN_LEVEL, "场地整平", false)', 'UIUtils.iconButton(Icons.TERRAIN_LEVEL, PlotI18n.tr("plugin.earthwork.level_site"), false)'),
    ('UIUtils.iconButton(Icons.RULER_VERTICAL, "高程测量", false)', 'UIUtils.iconButton(Icons.RULER_VERTICAL, PlotI18n.tr("plugin.earthwork.elevation_measure"), false)'),
    ('UIUtils.iconButton(Icons.CALCULATOR, "方量计算", false)', 'UIUtils.iconButton(Icons.CALCULATOR, PlotI18n.tr("plugin.earthwork.volume_calc"), false)'),
    ('UIUtils.iconButton(Icons.FILE_EXPORT, "导出报告", false)', 'UIUtils.iconButton(Icons.FILE_EXPORT, PlotI18n.tr("plugin.earthwork.export_report"), false)'),
])

# Road plugin - large batch
road_replacements = [
    ('"道路系统",\n            "用于规划和建造各种类型的道路"', '"plugin.road_system.name",\n            "plugin.road_system.desc"'),
    ('ImGui.text("道路预设")', 'ImGui.text(PlotI18n.tr("plugin.road.road_presets"))'),
    ('ImGui.text("路径信息")', 'ImGui.text(PlotI18n.tr("plugin.road.path_info"))'),
    ('ImGui.text(String.format("已选择路径: %.1f 米", pathLength))', 'ImGui.text(String.format(PlotI18n.tr("plugin.road.path_selected"), pathLength))'),
    ('ImGui.textColored((int) 0xFF4080FFFFL, "路径类型: " + getPathTypeName(selectedPath))',
     'ImGui.textColored((int) 0xFF4080FFFFL, PlotI18n.tr("plugin.road.path_type", getPathTypeName(selectedPath)))'),
    ('if (ImGui.button("编辑路径", ImGui.getContentRegionAvailX(), 0))', 'if (ImGui.button(PlotI18n.tr("plugin.road.edit_path"), ImGui.getContentRegionAvailX(), 0))'),
    ('String.format("找到 %d 个可用路径", availablePaths.size())', 'PlotI18n.tr("plugin.road.paths_found", availablePaths.size())'),
    ('ImGui.text("请选择一个路径图形以用于道路生成")', 'ImGui.text(PlotI18n.tr("plugin.road.select_path_hint"))'),
    ('ImGui.beginCombo("##select_path", "选择路径...")', 'ImGui.beginCombo("##select_path", PlotI18n.tr("plugin.road.select_path_combo"))'),
    ('String.format("%s (%.1f米)", getPathTypeName(path), calculatePathLength(path))',
     'String.format(PlotI18n.tr("plugin.road.path_combo_item"), getPathTypeName(path), calculatePathLength(path))'),
    ('ImGui.textColored((int) 0xFF808080FFL, "未找到路径")', 'ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.no_path_found"))'),
    ('ImGui.text("请使用绘图工具绘制路径（折线、自由绘制或贝塞尔曲线）")', 'ImGui.text(PlotI18n.tr("plugin.road.draw_path_hint"))'),
    ('ImGui.text("基本参数")', 'ImGui.text(PlotI18n.tr("plugin.road.basic_params"))'),
    ('ImGui.text("道路宽度: " + config.getRoadWidth() + " 方块")', 'ImGui.text(PlotI18n.tr("plugin.road.road_width", config.getRoadWidth()))'),
    ('ImGui.text("道路材质")', 'ImGui.text(PlotI18n.tr("plugin.road.material"))'),
    ('if (ImGui.beginCombo("##road_material", config.getSelectedMaterial())) {\n            String[] materials = {"混凝土", "石头", "砂砾", "木板"};\n            for (String material : materials) {\n                boolean isSelected = material.equals(config.getSelectedMaterial());\n                if (ImGui.selectable(material, isSelected)) {\n                    config.setSelectedMaterial(material);\n                }\n                if (isSelected) {\n                    ImGui.setItemDefaultFocus();\n                }\n            }\n            ImGui.endCombo();\n        }',
     'if (ImGui.beginCombo("##road_material", PlotI18n.tr(config.getSelectedMaterial()))) {\n            String[] materialKeys = {"material.plot.concrete", "material.plot.stone", "material.plot.gravel", "material.plot.planks"};\n            for (String materialKey : materialKeys) {\n                String materialLabel = PlotI18n.tr(materialKey);\n                boolean isSelected = materialKey.equals(config.getSelectedMaterial())\n                        || materialLabel.equals(config.getSelectedMaterial());\n                if (ImGui.selectable(materialLabel, isSelected)) {\n                    config.setSelectedMaterial(materialKey);\n                }\n                if (isSelected) {\n                    ImGui.setItemDefaultFocus();\n                }\n            }\n            ImGui.endCombo();\n        }'),
    ('ImGui.text("坡度与地形适应")', 'ImGui.text(PlotI18n.tr("plugin.road.slope_adaptation"))'),
    ('ImGui.text(String.format("最大坡度: %.1f%%", config.getMaxSlope()))', 'ImGui.text(String.format(PlotI18n.tr("plugin.road.max_slope"), config.getMaxSlope()))'),
    ('ImGui.text("桥阈值: " + config.getBridgeThreshold() + " 方块")', 'ImGui.text(PlotI18n.tr("plugin.road.bridge_threshold", config.getBridgeThreshold()))'),
    ('ImGui.text("隧道阈值: " + config.getTunnelThreshold() + " 方块")', 'ImGui.text(PlotI18n.tr("plugin.road.tunnel_threshold", config.getTunnelThreshold()))'),
    ('ImGui.text("附加设施")', 'ImGui.text(PlotI18n.tr("plugin.road.extra_facilities"))'),
    ('if (ImGui.checkbox("包含人行道", includeSidewalkRef))', 'if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk"), includeSidewalkRef))'),
    ('ImGui.text("人行道宽度: " + config.getSidewalkWidth() + " 方块")', 'ImGui.text(PlotI18n.tr("plugin.road.sidewalk_width", config.getSidewalkWidth()))'),
    ('if (ImGui.checkbox("包含路肩", includeShoulderRef))', 'if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_shoulder"), includeShoulderRef))'),
    ('ImGui.text("路肩宽度: " + config.getShoulderWidth() + " 方块")', 'ImGui.text(PlotI18n.tr("plugin.road.shoulder_width", config.getShoulderWidth()))'),
    ('if (ImGui.checkbox("包含排水沟", includeDrainageRef))', 'if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_drainage"), includeDrainageRef))'),
    ('ImGui.text("操作")', 'ImGui.text(PlotI18n.tr("plugin.road.operations"))'),
    ('if (ImGui.button("绘制路径", buttonWidth, 0))', 'if (ImGui.button(PlotI18n.tr("plugin.road.draw_path"), buttonWidth, 0))'),
    ('if (ImGui.button("计算预览", buttonWidth, 0))', 'if (ImGui.button(PlotI18n.tr("plugin.road.calc_preview"), buttonWidth, 0))'),
    ('if (ImGui.button("投影参考", buttonWidth, 0))', 'if (ImGui.button(PlotI18n.tr("plugin.road.projection_ref"), buttonWidth, 0))'),
    ('if (ImGui.button("实际构建", buttonWidth, 0))', 'if (ImGui.button(PlotI18n.tr("plugin.road.build"), buttonWidth, 0))'),
    ('ImGui.text("计算结果")', 'ImGui.text(PlotI18n.tr("plugin.road.calc_results"))'),
    ('ImGui.text("挖方量:")', 'ImGui.text(PlotI18n.tr("plugin.road.cut_volume"))'),
    ('ImGui.text(String.format("%d 方块", cutVolume))', 'ImGui.text(String.format(PlotI18n.tr("plugin.road.blocks_count"), cutVolume))'),
    ('ImGui.text("填方量:")', 'ImGui.text(PlotI18n.tr("plugin.road.fill_volume"))'),
    ('ImGui.text(String.format("%d 方块", fillVolume))', 'ImGui.text(String.format(PlotI18n.tr("plugin.road.blocks_count"), fillVolume))'),
    ('ImGui.text("桥梁数量:")', 'ImGui.text(PlotI18n.tr("plugin.road.bridge_count"))'),
    ('ImGui.text(String.format("%d 座", bridgeCount))', 'ImGui.text(String.format(PlotI18n.tr("plugin.road.bridges_count"), bridgeCount))'),
    ('ImGui.text("隧道数量:")', 'ImGui.text(PlotI18n.tr("plugin.road.tunnel_count"))'),
    ('ImGui.text(String.format("%d 段", tunnelCount))', 'ImGui.text(String.format(PlotI18n.tr("plugin.road.tunnels_count"), tunnelCount))'),
    ('ImGui.text(String.format("平衡度: %.1f%%", balancePercent))', 'ImGui.text(String.format(PlotI18n.tr("plugin.road.balance_percent"), balancePercent))'),
    ('ImGui.textColored((int) 0xFF808080FFL, "请先绘制路径并计算预览")', 'ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.preview_first"))'),
]
patch('com/plot/plugin/RoadSystemPlugin.java', road_replacements)

# Fix earthwork target elevation key - already correct in patch above

# Road config default material key
road_cfg = BASE / 'plugin/config/RoadSystemConfig.java'
rc = road_cfg.read_text(encoding='utf-8')
if 'material.plot.concrete' not in rc:
    rc = rc.replace('private String selectedMaterial = "混凝土";', 'private String selectedMaterial = "material.plot.concrete";')
    road_cfg.write_text(rc, encoding='utf-8')
    print('UPDATED plugin/config/RoadSystemConfig.java')

# Tool renderer patches
RENDERER_PATCHES = {
    'ui/panel/tool/renderer/AlignToolOptionRenderer.java': [
        ('ImGui.textDisabled("通过点对点交互完成对齐，无需选择模式")', 'ImGui.textDisabled(PlotI18n.tr("hint.plot.align.no_mode_needed"))'),
        ('ImGui.setTooltip("选择对齐方式，快捷键可直接切换")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.align.select_mode"))'),
        ('ImGui.textDisabled("以点对点选择为参考，无需选择参考模式")', 'ImGui.textDisabled(PlotI18n.tr("hint.plot.align.no_reference_needed"))'),
        ('ImGui.setTooltip("选择对齐的参考对象")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.align.select_reference"))'),
        ('ImGui.setTooltip("开启后将对齐时进行缩放以精确匹配目标尺寸")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.align.scale_on_align"))'),
        ('case LEFT -> "左对齐 (L)";', 'case LEFT -> PlotI18n.tr("align.plot.mode.left");'),
        ('case RIGHT -> "右对齐 (R)";', 'case RIGHT -> PlotI18n.tr("align.plot.mode.right");'),
        ('case CENTER -> "中心对齐 (C)";', 'case CENTER -> PlotI18n.tr("align.plot.mode.center");'),
        ('case TOP -> "顶部对齐 (T)";', 'case TOP -> PlotI18n.tr("align.plot.mode.top");'),
        ('case BOTTOM -> "底部对齐 (B)";', 'case BOTTOM -> PlotI18n.tr("align.plot.mode.bottom");'),
        ('case MIDDLE -> "中间对齐 (M)";', 'case MIDDLE -> PlotI18n.tr("align.plot.mode.middle");'),
        ('case DISTRIBUTE_H -> "水平分布";', 'case DISTRIBUTE_H -> PlotI18n.tr("align.plot.mode.distribute_h");'),
        ('case DISTRIBUTE_V -> "垂直分布";', 'case DISTRIBUTE_V -> PlotI18n.tr("align.plot.mode.distribute_v");'),
        ('case SELECTION_BOUNDS -> "选择边界";', 'case SELECTION_BOUNDS -> PlotI18n.tr("align.plot.ref.selection_bounds");'),
        ('case FIRST_SELECTED -> "第一个选中";', 'case FIRST_SELECTED -> PlotI18n.tr("align.plot.ref.first_selected");'),
        ('case LAST_SELECTED -> "最后选中";', 'case LAST_SELECTED -> PlotI18n.tr("align.plot.ref.last_selected");'),
        ('case LARGEST -> "最大图形";', 'case LARGEST -> PlotI18n.tr("align.plot.ref.largest");'),
    ],
    'ui/panel/tool/renderer/AnnotationToolOptionRenderer.java': [
        ('ImGui.setTooltip("距离标注：以方块为单位")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.annotation.distance_icon"))'),
        ('ImGui.setTooltip("角度标注：两条线段之间的家教")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.annotation.angle_icon"))'),
        ('ImGui.setTooltip("半径标注：标注圆形、半圆、圆弧的半径")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.annotation.radius_icon"))'),
        ('ImGui.setTooltip("区域标注：区域内的方块数量")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.annotation.area_icon"))'),
        ('ImGui.setTooltip("标注两点之间的距离（以方块为单位）")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.annotation.distance_btn"))'),
        ('ImGui.setTooltip("选中两条有夹角的直线，右键完成选中时自动标注角度")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.annotation.angle_btn"))'),
        ('ImGui.setTooltip("点选或框选圆形、半圆、圆弧图形，右键完成选中时自动标注半径")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.annotation.radius_btn"))'),
        ('ImGui.setTooltip("标注区域内的方块数量")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.annotation.area_btn"))'),
    ],
    'ui/panel/tool/renderer/ArrayToolOptionRenderer.java': [
        ('ImGui.setTooltip("矩形阵列：按行列排列图形")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.array.rectangular"))'),
        ('ImGui.setTooltip("环形阵列：按圆形排列图形")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.array.polar"))'),
        ('ImGui.setTooltip("路径阵列：沿路径等距分配（数量=点位数，含起点与终点）")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.array.path"))'),
        ('if (ImGui.treeNodeEx("矩形阵列参数", ImGuiTreeNodeFlags.DefaultOpen))', 'if (ImGui.treeNodeEx(PlotI18n.tr("array.plot.rectangular_params"), ImGuiTreeNodeFlags.DefaultOpen))'),
        ('ImGui.setTooltip("请先选择源图形和设置基准点")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.array.select_source_first"))'),
        ('ImGui.setTooltip("确认创建矩形阵列")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.array.confirm_rectangular"))'),
        ('if (ImGui.treeNodeEx("环形阵列参数", ImGuiTreeNodeFlags.DefaultOpen))', 'if (ImGui.treeNodeEx(PlotI18n.tr("array.plot.polar_params"), ImGuiTreeNodeFlags.DefaultOpen))'),
        ('ImGui.setTooltip("确认创建环形阵列")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.array.confirm_polar"))'),
        ('ImGui.textWrapped("操作：右键确认选中后，在画布左键点击一条路径；数量表示路径上的等距点位数（含起点与终点）")', 'ImGui.textWrapped(PlotI18n.tr("hint.plot.array.path_instructions"))'),
        ('ImGui.textDisabled("未拾取路径")', 'ImGui.textDisabled(PlotI18n.tr("hint.plot.array.no_path_picked"))'),
        ('ImGui.setTooltip("请先选择源图形和拾取路径")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.array.select_source_and_path"))'),
        ('ImGui.setTooltip("确认创建路径阵列")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.array.confirm_path"))'),
    ],
    'ui/panel/tool/renderer/TrimToolOptionRenderer.java': [
        ('private static final String[] FENCE_TYPE_LABELS = {"Polyline", "矩形", "圆形", "椭圆", "正多边形"};',
     'private String[] getFenceTypeLabels() {\n        return new String[] {\n            PlotI18n.tr("fence.plot.polyline"),\n            PlotI18n.tr("fence.plot.rectangle"),\n            PlotI18n.tr("fence.plot.circle"),\n            PlotI18n.tr("fence.plot.ellipse"),\n            PlotI18n.tr("fence.plot.polygon")\n        };\n    }'),
        ('ImGui.setTooltip("边界修剪：选择边界图形，然后点击要修剪的图形一侧")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.trim.boundary"))'),
        ('ImGui.setTooltip("栅栏修剪：定义栅栏线进行批量修剪")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.trim.fence"))'),
        ('ImGui.setTooltip("设置栅栏修剪边界类型")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.trim.fence_type"))'),
        ('ImGui.setTooltip("设置正多边形栅栏的边数")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.trim.fence_sides"))'),
        ('if (ImGui.treeNodeEx("修剪参数", ImGuiTreeNodeFlags.DefaultOpen))', 'if (ImGui.treeNodeEx(PlotI18n.tr("trim.plot.params"), ImGuiTreeNodeFlags.DefaultOpen))'),
        ('ImGui.setTooltip("修剪操作的容差范围，影响修剪点的检测精度")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.trim.tolerance"))'),
        ('if (ImGui.selectable(FENCE_TYPE_LABELS[i], selected))', 'if (ImGui.selectable(getFenceTypeLabels()[i], selected))'),
        ('case SELECTING_BOUNDARIES, WAITING_TRIM_CLICK, BOUNDARY_READY -> "边界图形";',
         'case SELECTING_BOUNDARIES, WAITING_TRIM_CLICK, BOUNDARY_READY -> PlotI18n.tr("trim.plot.item.boundary");'),
        ('case SELECTING_TARGETS, DRAWING_FENCE, FENCE_READY -> "目标图形";',
         'case SELECTING_TARGETS, DRAWING_FENCE, FENCE_READY -> PlotI18n.tr("trim.plot.item.target");'),
        ('default -> "图形";', 'default -> PlotI18n.tr("trim.plot.item.shape");'),
        ('"已选择 " + selectedCount + " 个" + itemType)',
         'PlotI18n.tr("trim.plot.selected_count", selectedCount, itemType)'),
        ('"栅栏点数: " + fencePointCount)', 'PlotI18n.tr("trim.plot.fence_points", fencePointCount)'),
    ],
    'ui/panel/tool/renderer/BreakToolOptionRenderer.java': [
        ('ImGui.setTooltip("单点打断：在任何图形的轮廓上点击，图形会从点击位置一分为二")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.break.single"))'),
        ('ImGui.setTooltip("两点打断：在同一条线上点击两点，删除两点之间的图形部分")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.break.two_point"))'),
    ],
    'ui/panel/tool/renderer/MirrorToolOptionRenderer.java': [
        ('ImGui.setTooltip("轴对称：关于一条轴线做对称（两点定义轴）")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.mirror.axis"))'),
        ('ImGui.setTooltip("中心对称：关于一个中心点做对称（等价于绕该点旋转180°）")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.mirror.center"))'),
    ],
    'ui/panel/tool/renderer/LineToolOptionRenderer.java': [
        ('ImGui.setTooltip("单线模式")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.line.single"))'),
        ('ImGui.setTooltip("多线模式")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.line.multi"))'),
    ],
    'ui/panel/tool/renderer/OffsetToolOptionRenderer.java': [
        ('ImGui.setTooltip("启用后可以连续偏移多个对象")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.offset.multiple"))'),
    ],
    'ui/panel/tool/renderer/RotateToolOptionRenderer.java': [
        ('ImGui.setTooltip("按住Shift键时的角度对齐步长（1°-90°）\\n双击可手动输入数值")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.rotate.angle_step"))'),
        ('ImGui.setTooltip("启用后旋转时会自动吸附到指定角度")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.rotate.angle_snap"))'),
        ('ImGui.setTooltip("设置为 " + presetValue + "° 角度步长")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.rotate.preset_step", presetValue))'),
    ],
    'ui/panel/tool/renderer/TransformToolOptionRenderer.java': [
        ('ImGui.setTooltip("启用后，可以在角点外侧拖拽进行旋转")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.transform.rotation_drag"))'),
    ],
    'ui/panel/tool/renderer/SelectionToolOptionRenderer.java': [
        ('ImGui.setTooltip("普通选择模式")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.select.normal"))'),
        ('ImGui.setTooltip("套索选择模式")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.select.lasso"))'),
    ],
    'ui/panel/tool/renderer/CatenaryLineToolOptionRenderer.java': [
        ('ImGui.setTooltip("标准模式：曲线对称，第三点控制弧垂深度")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.catenary.standard"))'),
        ('ImGui.setTooltip("样条插值模式：非对称曲线，第三点为控制点，可拖拽至任意位置")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.catenary.spline"))'),
    ],
    'ui/panel/tool/renderer/ChamferToolOptionRenderer.java': [
        ('ImGui.setTooltip("设置倒角斜面的距离大小")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.chamfer.distance"))'),
        ('ImGui.textColored(theme.errorText, "错误")', 'ImGui.textColored(theme.errorText, PlotI18n.tr("status.plot.error"))'),
        ('ImGui.textColored(theme.errorText, "渲染倒角工具选项时发生错误")', 'ImGui.textColored(theme.errorText, PlotI18n.tr("error.plot.chamfer_render"))'),
    ],
    'ui/panel/tool/renderer/FilletToolOptionRenderer.java': [
        ('ImGui.textColored(theme.errorText, "错误")', 'ImGui.textColored(theme.errorText, PlotI18n.tr("status.plot.error"))'),
        ('ImGui.textColored(theme.errorText, "渲染圆角工具选项时发生错误")', 'ImGui.textColored(theme.errorText, PlotI18n.tr("error.plot.fillet_render"))'),
    ],
    'ui/panel/tool/renderer/EraserToolOptionRenderer.java': [
        ('ImGui.textWrapped("调整橡皮擦大小，数值越大擦除范围越大")', 'ImGui.textWrapped(PlotI18n.tr("hint.plot.eraser.size"))'),
    ],
    'ui/panel/tool/renderer/TextToolOptionRenderer.java': [
        ('"样式、字号、对齐已移动到文字输入对话框中设置")', 'PlotI18n.tr("hint.plot.text.moved_to_dialog")'),
        ('ImGui.setTooltip("点击时弹出对话框输入文字")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.text.dialog_input"))'),
        ('ImGui.setTooltip("将字体大小重置为默认值")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.text.reset_font"))'),
        ('ImGui.setTooltip("将选中的文字转换为图形")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.text.convert_shape"))'),
        ('ImGui.setTooltip("快速减小值")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.text.decrease"))'),
        ('ImGui.setTooltip("快速增大值")', 'ImGui.setTooltip(PlotI18n.tr("hint.plot.text.increase"))'),
    ],
    'ui/panel/tool/renderer/ScaleToolOptionRenderer.java': [
        ('ImGui.textWrapped("缩放工具使用步骤：")', 'ImGui.textWrapped(PlotI18n.tr("hint.plot.scale.usage_title"))'),
        ('ImGui.bulletText("1. 使用选择工具选择要缩放的图形")', 'ImGui.bulletText(PlotI18n.tr("hint.plot.scale.step1"))'),
        ('ImGui.bulletText("2. 切换到缩放工具")', 'ImGui.bulletText(PlotI18n.tr("hint.plot.scale.step2"))'),
        ('ImGui.textColored(ThemeManager.getInstance().getCurrentTheme().warningText, "交互流程：")',
         'ImGui.textColored(ThemeManager.getInstance().getCurrentTheme().warningText, PlotI18n.tr("hint.plot.scale.workflow"))'),
        ('ImGui.bulletText("1. 使用选择工具选择图形")', 'ImGui.bulletText(PlotI18n.tr("hint.plot.scale.step3"))'),
        ('ImGui.bulletText("2. 切换到缩放工具，第一次点击或以选择框中心确定中心点")', 'ImGui.bulletText(PlotI18n.tr("hint.plot.scale.step4"))'),
        ('ImGui.bulletText("3. 第二次点击或拖动设置参考点并缩放，点击完成")', 'ImGui.bulletText(PlotI18n.tr("hint.plot.scale.step5"))'),
    ],
    'ui/panel/tool/renderer/PolygonToolOptionRenderer.java': [
        ('String[] tooltips = {"中心-顶点模式", "中心-半径模式"};',
         'String[] tooltips = {PlotI18n.tr("mode.plot.polygon.center_vertex_short"), PlotI18n.tr("mode.plot.polygon.center_radius_short")};'),
    ],
    'ui/panel/tool/renderer/SpiralToolOptionRenderer.java': [
        ('ImGui.textColored(theme.successText, "✓ 已确定中心点")', 'ImGui.textColored(theme.successText, PlotI18n.tr("hint.plot.spiral.center_set"))'),
        ('ImGui.textColored(theme.successText, "✓ 已确定螺旋起点")', 'ImGui.textColored(theme.successText, PlotI18n.tr("hint.plot.spiral.start_set"))'),
        ('ImGui.textColored(theme.successText, "✓ 已确定螺距")', 'ImGui.textColored(theme.successText, PlotI18n.tr("hint.plot.spiral.pitch_set"))'),
        ('ImGui.textColored(theme.successText, "✓ 已确定最外圈")', 'ImGui.textColored(theme.successText, PlotI18n.tr("hint.plot.spiral.outer_set"))'),
        ('renderFloatSlider("生长因子"', 'renderFloatSlider(PlotI18n.tr("option.plot.growth_factor")'),
        ('renderFloatSlider("扩张率"', 'renderFloatSlider(PlotI18n.tr("option.plot.expansion_rate")'),
        ('renderFloatSlider("螺旋系数"', 'renderFloatSlider(PlotI18n.tr("option.plot.spiral_coefficient")'),
        ('renderFloatSlider("螺距"', 'renderFloatSlider(PlotI18n.tr("option.plot.pitch")'),
    ],
}

for path, reps in RENDERER_PATCHES.items():
    patch(path, reps)

# Extend tool - dedicated file rewrite sections
extend_path = BASE / 'ui/panel/tool/renderer/ExtendToolOptionRenderer.java'
ext = extend_path.read_text(encoding='utf-8')
ext = ensure_import(ext)
ext = ext.replace(
    'String.format("已选择 %d 个边界图形", boundaryCount)',
    'String.format(PlotI18n.tr("hint.plot.extend.boundaries_selected"), boundaryCount)'
)
ext = ext.replace(
    'ImGui.textColored(theme.infoText, "状态: " + stateDescription)',
    'ImGui.textColored(theme.infoText, PlotI18n.tr("hint.plot.extend.status", stateDescription))'
)
ext = ext.replace(
    'ImGui.textColored(theme.warningText, "模式: 自动延伸")',
    'ImGui.textColored(theme.warningText, PlotI18n.tr("hint.plot.extend.mode_auto"))'
)
ext = ext.replace(
    'ImGui.textColored(theme.mutedText, "提示: " + operationHint)',
    'ImGui.textColored(theme.mutedText, PlotI18n.tr("hint.plot.extend.hint", operationHint))'
)
ext = ext.replace(
    'ImGui.textColored(theme.warningText, "✓ 延伸模式已激活，可连续延伸多个图形")',
    'ImGui.textColored(theme.warningText, PlotI18n.tr("hint.plot.extend.continuous_active"))'
)
ext = ext.replace(
    'ImGui.textColored(theme.errorText, "未选择边界图形")',
    'ImGui.textColored(theme.errorText, PlotI18n.tr("hint.plot.extend.no_boundary"))'
)
ext = ext.replace(
    'ImGui.textColored(theme.mutedText, "提示: 请先选择边界图形，然后右键确认")',
    'ImGui.textColored(theme.mutedText, PlotI18n.tr("hint.plot.extend.select_boundary_first"))'
)
ext = ext.replace(
    'ImGui.textColored(theme.successText, "自动延伸模式")',
    'ImGui.textColored(theme.successText, PlotI18n.tr("hint.plot.extend.auto_mode_title"))'
)
ext = ext.replace(
    'ImGui.textWrapped("工具会自动选择最佳延伸方式：先尝试标准延伸，如果没有交点则自动使用投影延伸。")',
    'ImGui.textWrapped(PlotI18n.tr("hint.plot.extend.auto_mode_desc"))'
)
ext = ext.replace(
    'return String.format("已选择 %d 个边界图形，右键确认边界选择", boundaryCount);',
    'return String.format(PlotI18n.tr("hint.plot.extend.boundary_confirmed"), boundaryCount);'
)
ext = ext.replace(
    'return "请选择边界图形，支持点选和框选";',
    'return PlotI18n.tr("hint.plot.extend.select_boundary_hint");'
)
ext = ext.replace(
    'return String.format("延伸模式已激活，点击要延伸的图形端点执行延伸（当前边界数: %d）", boundaryCount);',
    'return String.format(PlotI18n.tr("hint.plot.extend.extending"), boundaryCount);'
)
ext = ext.replace(
    'return "请按照提示进行操作";',
    'return PlotI18n.tr("hint.plot.extend.follow_prompt");'
)
extend_path.write_text(ext, encoding='utf-8')
print('UPDATED ui/panel/tool/renderer/ExtendToolOptionRenderer.java')

# Road getPathTypeName i18n
road_path = BASE / 'plugin/RoadSystemPlugin.java'
road = road_path.read_text(encoding='utf-8')
if 'PlotI18n.tr("path.plot.' not in road:
    road = ensure_import(road)
    road = road.replace(
        'return "折线";',
        'return PlotI18n.tr("path.plot.polyline");'
    ).replace(
        'return "自由绘制";',
        'return PlotI18n.tr("path.plot.freedraw");'
    ).replace(
        'return "贝塞尔曲线";',
        'return PlotI18n.tr("path.plot.bezier");'
    ).replace(
        'return "未知";',
        'return PlotI18n.tr("path.plot.unknown");'
    )
    road_path.write_text(road, encoding='utf-8')
    print('UPDATED RoadSystemPlugin getPathTypeName')

merge_lang()
print('Done hints and plugins')
