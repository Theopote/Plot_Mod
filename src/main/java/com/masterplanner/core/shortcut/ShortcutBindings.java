package com.masterplanner.core.shortcut;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.masterplanner.core.log.LogManager;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 快捷键绑定与持久化（简化版）
 * 负责：
 * - 默认映射
 * - 运行时更新
 * - 导入导出（到 masterplanner/keymap.json）
 */
public class ShortcutBindings {
    public record ActionDef(String actionId, String displayName) {}

    private static final ShortcutBindings INSTANCE = new ShortcutBindings();
    private final Map<String, String> actionToShortcut; // actionId -> shortcut string (lowercase)
    private final List<ActionDef> actions;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private com.masterplanner.api.shortcut.IShortcutListener router;

    private ShortcutBindings() {
        this.actionToShortcut = new LinkedHashMap<>();
        this.actions = new ArrayList<>();
        this.configPath = initConfigPath();
        defineActions();
        loadOrDefault();
        registerToShortcutManager();
    }

    public static ShortcutBindings getInstance() { return INSTANCE; }

    // 提供对话框列出
    public List<ActionDef> getAllActions() { return Collections.unmodifiableList(actions); }

    public String getBindingDisplay(String actionId) {
        String s = actionToShortcut.get(actionId);
        return s == null ? null : s;
    }

    public boolean updateBinding(String actionId, String shortcut) {
        if (actionId == null || shortcut == null) return false;
        String normalized = shortcut.toLowerCase(Locale.ROOT);

        // 简单冲突处理：如果其他动作使用了相同快捷键，移除其绑定
        String conflictedAction = findActionByShortcut(normalized);
        if (conflictedAction != null && !conflictedAction.equals(actionId)) {
            actionToShortcut.remove(conflictedAction);
        }

        actionToShortcut.put(actionId, normalized);
        save();
        registerToShortcutManager();
        return true;
    }

    public void clearBinding(String actionId) {
        actionToShortcut.remove(actionId);
        save();
        registerToShortcutManager();
    }

    public void resetToDefault() {
        actionToShortcut.clear();
        setDefaults();
        save();
        registerToShortcutManager();
    }

    public void exportBindings() {
        save();
    }

    public void importBindings() {
        loadOrDefault();
        registerToShortcutManager();
    }

    // 绑定到全局 ShortcutManager：注册监听器以分发动作
    private void registerToShortcutManager() {
        ShortcutManager mgr = ShortcutManager.getInstance();
        if (router == null) {
            // 注册一个总线监听器：把具体快捷转为动作回调
            router = new ShortcutRouter();
            mgr.addListener(router);
        }
    }

    private class ShortcutRouter implements com.masterplanner.api.shortcut.IShortcutListener {
        @Override
        public boolean onShortcutTriggered(String shortcut) {
            String actionId = findActionByShortcut(shortcut == null ? null : shortcut.toLowerCase(Locale.ROOT));
            if (actionId == null) return false;
            return ActionDispatcher.dispatch(actionId);
        }

        @Override
        public int getPriority() { return 50; }

        @Override
        public String getDescription() { return "ShortcutBindings Router"; }
    }

    private String findActionByShortcut(String shortcut) {
        if (shortcut == null) return null;
        for (Map.Entry<String, String> e : actionToShortcut.entrySet()) {
            if (shortcut.equals(e.getValue())) return e.getKey();
        }
        return null;
    }

    private void defineActions() {
        // 工具切换
        actions.add(new ActionDef("tool.select", "选择"));
        actions.add(new ActionDef("tool.eraser", "橡皮擦"));
        actions.add(new ActionDef("tool.line", "线段"));
        actions.add(new ActionDef("tool.free", "自由绘制"));
        actions.add(new ActionDef("tool.circle", "圆形"));
        actions.add(new ActionDef("tool.rectangle", "矩形"));
        actions.add(new ActionDef("tool.ellipse", "椭圆形"));
        actions.add(new ActionDef("tool.semicircle", "半圆"));
        actions.add(new ActionDef("tool.arc", "圆弧"));

        // 全局编辑
        actions.add(new ActionDef("file.new", "新建"));
        actions.add(new ActionDef("edit.undo", "撤销"));
        actions.add(new ActionDef("edit.redo", "重做"));
        actions.add(new ActionDef("file.save", "保存"));
        actions.add(new ActionDef("file.open", "打开"));
        actions.add(new ActionDef("file.export", "导出"));

        // 设置入口
        actions.add(new ActionDef("open.settings", "打开设置与帮助"));
        actions.add(new ActionDef("open.keycheatsheet", "打开快捷键速查"));
    }

    private void setDefaults() {
        // 单键（不区分大小写）
        actionToShortcut.put("tool.select", "space");
        actionToShortcut.put("tool.eraser", "d");
        actionToShortcut.put("tool.line", "l");
        actionToShortcut.put("tool.free", "p");
        actionToShortcut.put("tool.circle", "c");
        actionToShortcut.put("tool.rectangle", "r");
        actionToShortcut.put("tool.ellipse", "e");
        actionToShortcut.put("tool.semicircle", "s");
        actionToShortcut.put("tool.arc", "a");

        // 组合键
        actionToShortcut.put("file.new", "ctrl+n");
        actionToShortcut.put("edit.undo", "ctrl+z");
        actionToShortcut.put("edit.redo", "ctrl+y");
        actionToShortcut.put("file.save", "ctrl+s");
        actionToShortcut.put("file.open", "ctrl+o");
        actionToShortcut.put("file.export", "ctrl+e");

        actionToShortcut.put("open.settings", "ctrl+comma");
        actionToShortcut.put("open.keycheatsheet", "f1");
    }

    private Path initConfigPath() {
        // 使用 ConfigManager 的目录（gameDir/masterplanner）
        try {
            java.nio.file.Path base = java.nio.file.Paths.get(
                    net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toString(),
                    "masterplanner");
            if (!Files.exists(base)) Files.createDirectories(base);
            return base.resolve("keymap.json");
        } catch (Exception e) {
            LogManager.getInstance().error("初始化快捷键配置路径失败", e);
            return null;
        }
    }

    private void loadOrDefault() {
        if (configPath == null || !Files.exists(configPath)) {
            setDefaults();
            return;
        }
        try (FileReader r = new FileReader(configPath.toFile())) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = gson.fromJson(r, type);
            actionToShortcut.clear();
            if (loaded != null) actionToShortcut.putAll(loaded);
            // 确保新增动作有默认值
            Map<String, String> defaults = new LinkedHashMap<>();
            setDefaults(); // 写入到 actionToShortcut（临时）
            defaults.putAll(actionToShortcut);
            // 恢复加载内容覆盖默认
            actionToShortcut.clear();
            actionToShortcut.putAll(defaults);
            if (loaded != null) actionToShortcut.putAll(loaded);
        } catch (IOException e) {
            LogManager.getInstance().error("读取快捷键配置失败，使用默认值", e);
            setDefaults();
        }
    }

    private void save() {
        if (configPath == null) return;
        try (FileWriter w = new FileWriter(configPath.toFile())) {
            gson.toJson(actionToShortcut, w);
        } catch (IOException e) {
            LogManager.getInstance().error("保存快捷键配置失败", e);
        }
    }

    /**
     * 动作分发器：根据动作 ID 触发实际行为
     */
    public static class ActionDispatcher {
        public static boolean dispatch(String actionId) {
            if (actionId == null) return false;
            return switch (actionId) {
                case "tool.select" -> ToolActions.activate("select");
                case "tool.eraser" -> ToolActions.activate("eraser");
                case "tool.line" -> ToolActions.activate("line");
                case "tool.free" -> ToolActions.activate("freedraw");
                case "tool.circle" -> ToolActions.activate("circle");
                case "tool.rectangle" -> ToolActions.activate("rectangle");
                case "tool.ellipse" -> ToolActions.activate("ellipse");
                case "tool.semicircle" -> ToolActions.activate("semicircle");
                case "tool.arc" -> ToolActions.activate("arc");

                case "file.new" -> UiActions.openNewDialog();
                case "file.save" -> UiActions.openSaveDialog();
                case "file.open" -> UiActions.openImportDialog();
                case "file.export" -> UiActions.openExportDialog();
                case "edit.undo" -> UiActions.publishUndo();
                case "edit.redo" -> UiActions.publishRedo();

                case "open.settings" -> UiActions.openSettings();
                case "open.keycheatsheet" -> UiActions.openCheatSheet();
                default -> false;
            };
        }
    }

    // 实际行为实现（调用现有 ControlPanel 对话框或事件）
    static class UiActions {
        static boolean openNewDialog() {
            try {
                com.masterplanner.ui.dialog.NewFileDialog d = new com.masterplanner.ui.dialog.NewFileDialog(
                        com.masterplanner.core.state.AppState.getInstance(),
                        com.masterplanner.infrastructure.event.EventBus.getInstance(), null);
                d.show();
                return true;
            } catch (Exception e) { return false; }
        }
        static boolean openSaveDialog() {
            try {
                com.masterplanner.ui.dialog.SaveFileDialog d = new com.masterplanner.ui.dialog.SaveFileDialog(
                        com.masterplanner.core.state.AppState.getInstance(),
                        com.masterplanner.infrastructure.event.EventBus.getInstance(), null);
                d.show();
                return true;
            } catch (Exception e) { return false; }
        }
        static boolean openImportDialog() {
            try {
                com.masterplanner.ui.dialog.ImportFileDialog d = new com.masterplanner.ui.dialog.ImportFileDialog(
                        com.masterplanner.core.state.AppState.getInstance(),
                        com.masterplanner.infrastructure.event.EventBus.getInstance(), null);
                d.show();
                return true;
            } catch (Exception e) { return false; }
        }
        static boolean openExportDialog() { return false; }
        static boolean publishUndo() {
            com.masterplanner.infrastructure.event.EventBus.getInstance().publish(
                    new com.masterplanner.infrastructure.event.command.UndoEvent());
            return true;
        }
        static boolean publishRedo() {
            com.masterplanner.infrastructure.event.EventBus.getInstance().publish(
                    new com.masterplanner.infrastructure.event.command.RedoEvent());
            return true;
        }
        static boolean openSettings() {
            com.masterplanner.ui.dialog.SettingsAndHelpDialog.getInstance().open();
            return true;
        }
        static boolean openCheatSheet() { return false; }
    }

    static class ToolActions {
        static boolean activate(String toolId) {
            try {
                com.masterplanner.core.tool.ToolManager toolManager = com.masterplanner.core.tool.ToolManager.getInstance();
                com.masterplanner.api.tool.ITool tool = toolManager.getTool(toolId);
                if (tool != null) {
                    toolManager.activateTool(tool);
                    return true;
                }
            } catch (Exception e) {
                LogManager.getInstance().warn("无法激活工具: {}", toolId);
            }
            return false;
        }
    }
}


