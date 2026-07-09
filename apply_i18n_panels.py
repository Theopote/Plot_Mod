#!/usr/bin/env python3
"""Add i18n keys for tool panel tooltips, control panel, layers, and block config."""
import json
from pathlib import Path

LANG_DIR = Path('src/main/resources/assets/plot/lang')

NEW_KEYS = {
    "toolbar.plot.settings_help": ("Plot Settings & Help", "Plot 设置与帮助"),
    "toolbar.plot.block_config_tooltip": ("Configure the block palette for projection and building", "配置方块调色盘，用于投影与建造"),
    "toolbar.plot.clear_canvas_tooltip": ("Clear all shapes on the canvas (undoable)", "清空画布上的所有图形（可撤销）"),
    "layer.plot.toolbar.new_layer": ("New Layer", "新建图层"),
    "layer.plot.toolbar.delete_selected": ("Delete selected layers", "删除选中图层"),
    "layer.plot.toolbar.delete_unlock_first": ("Cannot delete locked layers. Unlock them first.", "无法删除锁定的图层，请先解锁"),
    "layer.plot.toolbar.merge_need_two": ("Select at least two layers to merge", "请选择至少两个图层进行合并"),
    "layer.plot.toolbar.move_up": ("Move selected layers up", "上移选中图层"),
    "layer.plot.toolbar.move_down": ("Move selected layers down", "下移选中图层"),
    "layer.plot.toolbar.select_to_move": ("Please select layers to move first", "请先选择要移动的图层"),
    "layer.plot.toolbar.select_all_elements": ("Select all elements in the current layer", "选择当前图层的所有图元"),
    "layer.plot.toolbar.no_active_layer": ("No active layer", "没有活动图层"),
    "layer.plot.toolbar.cannot_merge_locked": ("Cannot merge locked layers: %s", "无法合并锁定的图层: %s"),
    "layer.plot.merged_default_name": ("Merged Layer", "合并图层"),
    "layer.plot.merge_create_failed": ("Failed to create merged layer: %s", "创建合并图层失败: %s"),
    "layer.plot.merge_type_error": ("Created layer type is incorrect", "创建的图层类型不正确"),
    "layer.plot.merge_failed": ("Failed to merge layers: %s", "合并图层失败: %s"),
    "layer.plot.move_failed": ("Failed to move layer: %s", "移动图层失败：%s"),
    "layer.plot.locked_no_line_style": ("Layer is locked; cannot change line style", "图层已锁定，无法修改线型"),
    "layer.plot.locked_no_line_width": ("Layer is locked; cannot change line width", "图层已锁定，无法修改线宽"),
    "layer.plot.locked_no_color": ("Layer is locked; cannot change color", "图层已锁定，无法修改颜色"),
    "layer.plot.locked_no_rename": ("Layer is locked; cannot rename", "图层已锁定，无法编辑名称"),
    "layer.plot.name_empty": ("Layer name cannot be empty", "图层名称不能为空"),
    "layer.plot.name_too_long": ("Layer name is too long; use a shorter name", "图层名称过长，请使用较短的名称"),
    "layer.plot.name_too_long_max": ("Layer name cannot exceed %d characters", "图层名称不能超过%d个字符"),
    "layer.plot.name_invalid_chars": ("Layer name can only contain Chinese, letters, numbers, or underscores", "图层名称只能包含中文、字母、数字或下划线"),
    "layer.plot.name_exists": ("Layer name already exists", "图层名称已存在"),
    "layer.plot.name_exists_alt": ("Layer name already exists; choose another name", "图层名称已存在，请使用其他名称"),
    "layer.plot.default_name": ("Layer%d", "图层%d"),
    "layer.plot.copy_suffix": (" copy", " 副本"),
    "layer.plot.copy_failed": ("Failed to duplicate layer: %s", "复制图层失败：%s"),
    "layer.plot.create_failed": ("Failed to create layer", "创建图层失败"),
    "layer.plot.merge_need_two": ("Select at least two layers to merge", "需要选择至少两个图层才能合并"),
    "layer.plot.cannot_merge_locked_ctx": ("Cannot merge locked layers", "无法合并锁定的图层"),
    "layer.plot.merge_has_locked_menu": ("Selected layers include locked layers", "选中的图层中包含锁定图层"),
    "layer.plot.merge_need_multiple_menu": ("Multiple layers must be selected", "需要选择多个图层"),
    "block.plot.title": ("Block Config", "方块配置"),
    "block.plot.search": ("Search Blocks", "搜索方块"),
    "block.plot.search_hint": ("Search block name / ID…", "搜索方块名称 / ID…"),
    "block.plot.search_placeholder": ("Search blocks...", "搜索方块..."),
    "block.plot.searching": ("Searching...", "搜索中..."),
    "block.plot.min_chars": ("Enter at least %d characters", "请输入至少 %d 个字符"),
    "block.plot.display_count": ("Showing %d blocks", "显示 %d 个方块"),
    "block.plot.palette": ("Palette", "调色盘"),
    "block.plot.palette_hint": ("Left-click add · Right-click remove · Left-click swap", "左键添加 · 右键移除 · 左键换位"),
    "block.plot.selected_count": ("Selected %d / %d blocks", "已选 %d / %d 个方块"),
    "block.plot.palette_full": ("Palette is full", "调色盘已满"),
    "block.plot.apply": ("✓ Apply", "✓ 应用"),
    "block.plot.cancel": ("Cancel", "取消"),
    "block.plot.clear": ("Clear", "清空"),
    "block.plot.page_prev": ("← Prev", "← 上页"),
    "block.plot.page_next": ("Next →", "下页 →"),
    "block.plot.no_results": ("No results (%d blocks in category)", "无结果（此分类总共：%d 方块）"),
    "block.plot.page_info": ("%d / %d (%d blocks in category)", "%d / %d（此分类总共：%d 方块）"),
    "block.plot.category.building_blocks": ("Building Blocks", "建筑方块"),
    "block.plot.category.colored_blocks": ("Colored Blocks", "染色方块"),
    "block.plot.category.natural_terrain": ("Natural Terrain", "自然地形"),
    "block.plot.category.plants_foliage": ("Plants & Foliage", "植物与树叶"),
    "block.plot.category.redstone_mechanisms": ("Redstone & Mechanisms", "红石与机械"),
    "block.plot.category.functional_utility": ("Functional & Utility", "功能与设施"),
    "block.plot.category.decorative_blocks": ("Decorative Blocks", "装饰方块"),
    "block.plot.category.light_sources": ("Light Sources", "光源方块"),
    "block.plot.category.liquids_environment": ("Liquids & Environment", "液体与环境"),
    "block.plot.category.miscellaneous": ("Miscellaneous", "杂项"),
    "block.plot.scope.all": ("All", "全部"),
    "block.plot.scope.name": ("Name", "名称"),
    "block.plot.scope.id": ("ID", "ID"),
}

def merge_lang():
    en = json.loads((LANG_DIR / 'en_us.json').read_text(encoding='utf-8'))
    zh = json.loads((LANG_DIR / 'zh_cn.json').read_text(encoding='utf-8'))
    for key, (en_val, zh_val) in NEW_KEYS.items():
        en[key] = en_val
        zh[key] = zh_val
    (LANG_DIR / 'en_us.json').write_text(json.dumps(en, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (LANG_DIR / 'zh_cn.json').write_text(json.dumps(zh, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Merged {len(NEW_KEYS)} keys; total {len(en)}')

if __name__ == '__main__':
    merge_lang()
