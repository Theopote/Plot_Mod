#!/usr/bin/env python3
from pathlib import Path
import re

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

def patch(rel, replacements):
    p = BASE / rel
    text = p.read_text(encoding='utf-8')
    orig = text
    for old, new in replacements:
        text = text.replace(old, new)
    text = ensure_import(text)
    if text != orig:
        p.write_text(text, encoding='utf-8')
        print(f'UPDATED {rel}')

patch('ui/panel/extension/ExtensionPanel.java', [
    ('ImGui.textColored(theme.errorText, "插件未启用")', 'ImGui.textColored(theme.errorText, PlotI18n.tr("panel.plot.extension_disabled"))'),
    ('ImGui.textColored(theme.errorText, "渲染错误: " + e.getMessage())', 'ImGui.textColored(theme.errorText, PlotI18n.tr("panel.plot.extension_render_error", e.getMessage()))'),
    ('ImGui.textColored(theme.mutedText, "请选择一个插件")', 'ImGui.textColored(theme.mutedText, PlotI18n.tr("panel.plot.extension_select_plugin"))'),
    ('ImGui.textWrapped("从上方列表中选择一个插件以查看和配置其参数")', 'ImGui.textWrapped(PlotI18n.tr("panel.plot.extension_select_hint"))'),
])

patch('plugin/ImageToolsPlugin.java', [
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

patch('plugin/EarthworkPlugin.java', [
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

patch('plugin/RoadSystemPlugin.java', [
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
    ('return switch (material) {\n            case "混凝土" -> "minecraft:white_concrete";\n            case "砂砾" -> "minecraft:gravel";\n            case "木板" -> "minecraft:oak_planks";\n            default -> "minecraft:stone";\n        };',
     'return switch (material) {\n            case "material.plot.concrete", "混凝土" -> "minecraft:white_concrete";\n            case "material.plot.gravel", "砂砾" -> "minecraft:gravel";\n            case "material.plot.planks", "木板" -> "minecraft:oak_planks";\n            case "material.plot.stone", "石头" -> "minecraft:stone";\n            default -> "minecraft:stone";\n        };'),
])

print('Done plugins fix')
