#!/usr/bin/env python3
"""Batch-14: validation, init guard, and render error i18n."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
SRC = ROOT / "src/main/java/com/plot"
LANG = ROOT / "src/main/resources/assets/plot/lang"

KEYS = {
    "error.plot.validation.app_state_null": ("AppState cannot be null", "AppState 不能为空"),
    "error.plot.validation.event_bus_null": ("EventBus cannot be null", "EventBus 不能为空"),
    "error.plot.validation.layer_manager_null": ("LayerManager cannot be null", "LayerManager 不能为空"),
    "error.plot.validation.tool_manager_null": ("ToolManager cannot be null", "ToolManager 不能为空"),
    "error.plot.validation.snap_manager_null": ("SnapManager cannot be null", "SnapManager 不能为空"),
    "error.plot.validation.command_manager_null": ("CommandManager cannot be null", "CommandManager 不能为空"),
    "error.plot.validation.shortcut_manager_null": ("ShortcutManager cannot be null", "ShortcutManager 不能为空"),
    "error.plot.validation.transform_handler_null": ("TransformHandler cannot be null", "TransformHandler 不能为空"),
    "error.plot.validation.bounding_box_control_manager_null": (
        "BoundingBoxControlManager cannot be null",
        "BoundingBoxControlManager 不能为空",
    ),
    "error.plot.validation.interaction_type_null": ("InteractionType cannot be null", "InteractionType 不能为空"),
    "error.plot.validation.app_state_init_failed": (
        "AppState failed to initialize correctly",
        "AppState 未能正确初始化",
    ),
    "error.plot.validation.event_bus_init_failed": (
        "EventBus failed to initialize correctly",
        "EventBus 未能正确初始化",
    ),
    "error.plot.validation.layer_manager_init_failed": (
        "LayerManager failed to initialize correctly",
        "LayerManager 未能正确初始化",
    ),
    "error.plot.validation.app_state_not_initialized": ("AppState is not initialized", "AppState 未初始化"),
    "error.plot.validation.event_bus_not_initialized": ("EventBus is not initialized", "EventBus 未初始化"),
    "error.plot.validation.layer_manager_not_initialized": (
        "LayerManager is not initialized",
        "LayerManager 未初始化",
    ),
    "error.plot.validation.tool_manager_not_initialized": (
        "ToolManager must be initialized via initialize(appState) first",
        "ToolManager 必须先通过 initialize(appState) 初始化",
    ),
    "error.plot.validation.tool_manager_init_failed": (
        "ToolManager initialization failed",
        "ToolManager 初始化失败",
    ),
    "error.plot.validation.app_state_layer_manager_null": (
        "LayerManager in AppState cannot be null",
        "AppState 中的 LayerManager 不能为空",
    ),
    "error.plot.validation.canvas_prereq": (
        "AppState and its LayerManager must be initialized before creating Canvas",
        "AppState 及其 LayerManager 必须在创建 Canvas 前初始化",
    ),
    "error.plot.validation.app_state_before_canvas_core": (
        "AppState must be initialized before creating CanvasCore",
        "AppState 必须在创建 CanvasCore 之前初始化",
    ),
    "error.plot.validation.layer_manager_before_canvas_core": (
        "LayerManager in AppState must be initialized before creating CanvasCore",
        "AppState 中的 LayerManager 必须在创建 CanvasCore 之前初始化",
    ),
    "error.plot.validation.canvas_not_in_app_state": (
        "Canvas is not initialized in AppState",
        "Canvas 未在 AppState 中初始化",
    ),
    "error.plot.validation.utility_class": (
        "Utility class cannot be instantiated",
        "工具类不能被实例化",
    ),
    "error.plot.validation.constants_class": (
        "Constants class cannot be instantiated",
        "常量类不能被实例化",
    ),
    "error.plot.render.offscreen_empty": (
        "Off-screen item render result is empty (transparent texture)",
        "离屏物品渲染结果为空（透明纹理）",
    ),
    "error.plot.render.draw_item_failed": (
        "DrawContext.drawItemWithoutEntity / drawItem call failed",
        "DrawContext.drawItemWithoutEntity / drawItem 调用失败",
    ),
    "error.plot.render.gui_state_unavailable": (
        "Unable to access GameRenderer.guiState",
        "无法获取 GameRenderer.guiState",
    ),
}

REPLACEMENTS = {
    "ui/panel/layer/LayerPanel.java": [
        ('throw new IllegalStateException("AppState未能正确初始化")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.validation.app_state_init_failed"))'),
        ('throw new IllegalStateException("EventBus未能正确初始化")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.validation.event_bus_init_failed"))'),
        ('throw new IllegalStateException("LayerManager未能正确初始化")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.validation.layer_manager_init_failed"))'),
        ('throw new IllegalStateException("AppState未初始化")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.validation.app_state_not_initialized"))'),
        ('throw new IllegalStateException("EventBus未初始化")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.validation.event_bus_not_initialized"))'),
        ('throw new IllegalStateException("LayerManager未初始化")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.validation.layer_manager_not_initialized"))'),
    ],
    "ui/canvas/CanvasCore.java": [
        ('throw new IllegalArgumentException("AppState不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.app_state_null"))'),
        ('throw new IllegalArgumentException("AppState中的LayerManager不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.app_state_layer_manager_null"))'),
        ('throw new IllegalArgumentException("LayerManager不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.layer_manager_null"))'),
        ('throw new IllegalStateException("AppState必须在创建CanvasCore之前初始化")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.validation.app_state_before_canvas_core"))'),
        ('throw new IllegalStateException("AppState中的LayerManager必须在创建CanvasCore之前初始化")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.validation.layer_manager_before_canvas_core"))'),
    ],
    "ui/canvas/Canvas.java": [
        ('throw new IllegalArgumentException("AppState 及其 LayerManager 必须在创建Canvas前初始化")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.canvas_prereq"))'),
    ],
    "ui/tools/DrawingToolsModule.java": [
        ('throw new UnsupportedOperationException("DrawingToolsModule是工具类，不能被实例化")',
         'throw new UnsupportedOperationException(PlotI18n.error("error.plot.validation.utility_class"))'),
        ('throw new IllegalArgumentException("ToolManager不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.tool_manager_null"))'),
        ('throw new IllegalArgumentException("AppState不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.app_state_null"))'),
        ('throw new IllegalArgumentException("EventBus不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.event_bus_null"))'),
        ('throw new IllegalArgumentException("SnapManager不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.snap_manager_null"))'),
        ('throw new IllegalArgumentException("CommandManager不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.command_manager_null"))'),
    ],
    "ui/tools/ModifyToolsModule.java": [
        ('throw new UnsupportedOperationException("ModifyToolsModule是工具类，不能被实例化")',
         'throw new UnsupportedOperationException(PlotI18n.error("error.plot.validation.utility_class"))'),
        ('throw new IllegalArgumentException("ToolManager不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.tool_manager_null"))'),
        ('throw new IllegalArgumentException("AppState不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.app_state_null"))'),
        ('throw new IllegalArgumentException("EventBus不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.event_bus_null"))'),
        ('throw new IllegalArgumentException("SnapManager不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.snap_manager_null"))'),
        ('throw new IllegalArgumentException("CommandManager不能为null")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.command_manager_null"))'),
        ('throw new IllegalStateException("Canvas未在AppState中初始化")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.validation.canvas_not_in_app_state"))'),
    ],
    "ui/toolbar/ToolPanel.java": [
        ('throw new IllegalStateException("ToolManager初始化失败")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.validation.tool_manager_init_failed"))'),
    ],
    "core/tool/ToolManager.java": [
        ('throw new IllegalArgumentException("AppState 不能为空")',
         'throw new IllegalArgumentException(PlotI18n.error("error.plot.validation.app_state_null"))'),
        ('throw new IllegalStateException("ToolManager 必须先通过 initialize(appState) 初始化")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.validation.tool_manager_not_initialized"))'),
    ],
    "ui/component/BlockIconRenderer.java": [
        ('throw new IllegalStateException("离屏物品渲染结果为空（透明纹理）")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.render.offscreen_empty"))'),
        ('throw new IllegalStateException("DrawContext.drawItemWithoutEntity / drawItem 调用失败")',
         'throw new IllegalStateException(PlotI18n.error("error.plot.render.draw_item_failed"))'),
        ('throw new IllegalStateException("无法获取 GameRenderer.guiState", t)',
         'throw new IllegalStateException(PlotI18n.error("error.plot.render.gui_state_unavailable"), t)'),
    ],
    "ui/tools/impl/modify/constants/FilletConstants.java": [
        ('throw new UnsupportedOperationException("常量类不能实例化")',
         'throw new UnsupportedOperationException(PlotI18n.error("error.plot.validation.constants_class"))'),
    ],
    "ui/utils/GeometryCalculationUtils.java": [
        ('throw new UnsupportedOperationException("工具类不能实例化")',
         'throw new UnsupportedOperationException(PlotI18n.error("error.plot.validation.utility_class"))'),
    ],
    "ui/tools/impl/modify/strategy/TransformWithSelectionStrategy.java": [
        ('Objects.requireNonNull(transformHandler, "TransformHandler不能为null")',
         'Objects.requireNonNull(transformHandler, PlotI18n.error("error.plot.validation.transform_handler_null"))'),
        ('Objects.requireNonNull(controlManager, "BoundingBoxControlManager不能为null")',
         'Objects.requireNonNull(controlManager, PlotI18n.error("error.plot.validation.bounding_box_control_manager_null"))'),
        ('Objects.requireNonNull(eventBus, "EventBus不能为null")',
         'Objects.requireNonNull(eventBus, PlotI18n.error("error.plot.validation.event_bus_null"))'),
    ],
    "ui/tools/impl/modify/FilletTool.java": [
        ('Objects.requireNonNull(appState, "AppState 不能为空")',
         'Objects.requireNonNull(appState, PlotI18n.error("error.plot.validation.app_state_null"))'),
        ('Objects.requireNonNull(snapManager, "ISnapManager 不能为空")',
         'Objects.requireNonNull(snapManager, PlotI18n.error("error.plot.validation.snap_manager_null"))'),
    ],
    "ui/tools/impl/modify/ChamferTool.java": [
        ('Objects.requireNonNull(appState, "AppState 不能为空")',
         'Objects.requireNonNull(appState, PlotI18n.error("error.plot.validation.app_state_null"))'),
        ('Objects.requireNonNull(snapManager, "ISnapManager 不能为空")',
         'Objects.requireNonNull(snapManager, PlotI18n.error("error.plot.validation.snap_manager_null"))'),
    ],
    "ui/tools/impl/modify/ModifyTool.java": [
        ('Objects.requireNonNull(eventBus, "EventBus 不能为空")',
         'Objects.requireNonNull(eventBus, PlotI18n.error("error.plot.validation.event_bus_null"))'),
        ('Objects.requireNonNull(shortcutManager, "ShortcutManager 不能为空")',
         'Objects.requireNonNull(shortcutManager, PlotI18n.error("error.plot.validation.shortcut_manager_null"))'),
    ],
    "core/tool/BaseTool.java": [
        ('Objects.requireNonNull(appState, "AppState 不能为空")',
         'Objects.requireNonNull(appState, PlotI18n.error("error.plot.validation.app_state_null"))'),
        ('Objects.requireNonNull(eventBus, "EventBus 不能为空")',
         'Objects.requireNonNull(eventBus, PlotI18n.error("error.plot.validation.event_bus_null"))'),
        ('Objects.requireNonNull(shortcutManager, "ShortcutManager 不能为空")',
         'Objects.requireNonNull(shortcutManager, PlotI18n.error("error.plot.validation.shortcut_manager_null"))'),
    ],
    "ui/tools/impl/drawing/DrawingTool.java": [
        ('Objects.requireNonNull(eventBus, "EventBus 不能为空")',
         'Objects.requireNonNull(eventBus, PlotI18n.error("error.plot.validation.event_bus_null"))'),
        ('Objects.requireNonNull(shortcutManager, "ShortcutManager 不能为空")',
         'Objects.requireNonNull(shortcutManager, PlotI18n.error("error.plot.validation.shortcut_manager_null"))'),
        ('Objects.requireNonNull(interactionType, "InteractionType 不能为空")',
         'Objects.requireNonNull(interactionType, PlotI18n.error("error.plot.validation.interaction_type_null"))'),
    ],
    "ui/tools/snap/SnapHandler.java": [
        ('Objects.requireNonNull(appState, "AppState不能为空")',
         'Objects.requireNonNull(appState, PlotI18n.error("error.plot.validation.app_state_null"))'),
    ],
    "ui/tools/impl/drawing/helper/StyleHandler.java": [
        ('Objects.requireNonNull(appState, "AppState不能为空")',
         'Objects.requireNonNull(appState, PlotI18n.error("error.plot.validation.app_state_null"))'),
    ],
    "ui/tools/impl/modify/helper/TransformHandler.java": [
        ('Objects.requireNonNull(appState, "AppState不能为空")',
         'Objects.requireNonNull(appState, PlotI18n.error("error.plot.validation.app_state_null"))'),
    ],
}

IMPORT = "import com.plot.utils.PlotI18n;\n"


def ensure_import(content: str) -> str:
    if "import com.plot.utils.PlotI18n;" in content:
        return content
    if "package " in content:
        idx = content.find("\n\n")
        if idx != -1:
            return content[: idx + 2] + IMPORT + content[idx + 2 :]
    return IMPORT + content


def apply_replacements() -> int:
    total = 0
    for rel, pairs in REPLACEMENTS.items():
        path = SRC / rel
        text = path.read_text(encoding="utf-8")
        count = 0
        for old, new in pairs:
            if old not in text:
                print(f"WARN missing in {rel}: {old[:70]}...")
                continue
            text = text.replace(old, new)
            count += 1
        if count:
            text = ensure_import(text)
            path.write_text(text, encoding="utf-8")
            print(f"{rel}: {count} replacements")
            total += count
    return total


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
    n = apply_replacements()
    merge_lang(LANG / "en_us.json", 0)
    merge_lang(LANG / "zh_cn.json", 1)
    print(f"Done. Replacements: {n}, keys: {len(KEYS)}")
