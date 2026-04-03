package com.plot.ui.dialog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.plot.api.shortcut.IShortcutListener;
import com.plot.core.log.LogManager;
import com.plot.core.shortcut.ShortcutManager;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * UI层快捷键映射管理（与设置对话框配套的简化实现）
 */
public class KeymapManager {
    public static class ActionDef {
        private final String actionId;
        private final String displayName;
        private final String category;

        public ActionDef(String actionId, String displayName, String category) {
            this.actionId = actionId;
            this.displayName = displayName;
            this.category = category;
        }
        public String actionId() { return actionId; }
        public String displayName() { return displayName; }
        public String category() { return category; }
    }

    private static final KeymapManager INSTANCE = new KeymapManager();
    private final Map<String, String> actionToShortcut = new LinkedHashMap<>();
    private final Map<String, String> defaultBindings = new LinkedHashMap<>();
    private final List<ActionDef> actions = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private IShortcutListener router;

    private KeymapManager() {
        this.configPath = initConfigPath();
        defineActions();
        loadOrDefault();
        registerRouter();
    }

    public static KeymapManager getInstance() { return INSTANCE; }

    public List<ActionDef> getAllActions() { return Collections.unmodifiableList(actions); }
    public String getBindingDisplay(String actionId) { return actionToShortcut.get(actionId); }
    public String getDefaultBinding(String actionId) { return defaultBindings.get(actionId); }

    public String getActionDisplayName(String actionId) {
        if (actionId == null) return null;
        for (ActionDef def : actions) {
            if (actionId.equals(def.actionId())) {
                return def.displayName();
            }
        }
        return actionId;
    }

    public boolean updateBinding(String actionId, String shortcut) {
        if (actionId == null || shortcut == null) return false;
        updateBindingAndGetConflict(actionId, shortcut);
        return true;
    }

    /**
     * 更新动作绑定，并返回被占用同一快捷键的动作ID（如无冲突则返回 null）。
     */
    public String updateBindingAndGetConflict(String actionId, String shortcut) {
        if (actionId == null || shortcut == null) return null;
        String normalized = shortcut.toLowerCase(Locale.ROOT);
        // 简单冲突：移除旧动作占用
        String conflicted = findActionByShortcut(normalized);
        if (conflicted != null && !conflicted.equals(actionId)) actionToShortcut.remove(conflicted);
        actionToShortcut.put(actionId, normalized);
        save();
        return conflicted != null && !conflicted.equals(actionId) ? conflicted : null;
    }

    public void clearBinding(String actionId) { actionToShortcut.remove(actionId); save(); }
    public void resetToDefault() { actionToShortcut.clear(); setDefaults(); save(); }

    private void registerRouter() {
        if (router != null) return;
        router = new IShortcutListener() {
            @Override public boolean onShortcutTriggered(String shortcut) {
                String actionId = findActionByShortcut(shortcut == null ? null : shortcut.toLowerCase(Locale.ROOT));
                if (actionId == null) return false;
                return dispatch(actionId);
            }
            @Override public int getPriority() { return 50; }
            @Override public String getDescription() { return "KeymapManager Router"; }
        };
        ShortcutManager.getInstance().addListener(router);
    }

    private boolean dispatch(String actionId) { return UIShortcutActions.dispatch(actionId); }

    private String findActionByShortcut(String shortcut) {
        if (shortcut == null) return null;
        for (Map.Entry<String, String> e : actionToShortcut.entrySet()) {
            if (shortcut.equals(e.getValue())) return e.getKey();
        }
        return null;
    }

    private void defineActions() {
        // 工具切换
        actions.add(new ActionDef("tool.select", "选择", "绘图工具"));
        actions.add(new ActionDef("tool.eraser", "橡皮擦", "绘图工具"));
        actions.add(new ActionDef("tool.line", "线段", "绘图工具"));
        actions.add(new ActionDef("tool.free", "自由绘制", "绘图工具"));
        actions.add(new ActionDef("tool.circle", "圆形", "绘图工具"));
        actions.add(new ActionDef("tool.rectangle", "矩形", "绘图工具"));
        actions.add(new ActionDef("tool.ellipse", "椭圆形", "绘图工具"));
        actions.add(new ActionDef("tool.semicircle", "半圆", "绘图工具"));
        actions.add(new ActionDef("tool.arc", "圆弧", "绘图工具"));

        // 全局编辑
        actions.add(new ActionDef("edit.undo", "撤销", "编辑操作"));
        actions.add(new ActionDef("edit.redo", "重做", "编辑操作"));

        // 设置入口
        actions.add(new ActionDef("open.settings", "打开设置与帮助", "视图与面板"));
        actions.add(new ActionDef("open.keycheatsheet", "打开快捷键速查", "视图与面板"));
    }

    private void setDefaults() {
        if (defaultBindings.isEmpty()) {
            fillDefaults(defaultBindings);
        }
        actionToShortcut.putAll(defaultBindings);
    }

    private void fillDefaults(Map<String, String> target) {
        target.put("tool.select", "space");
        target.put("tool.eraser", "d");
        target.put("tool.line", "l");
        target.put("tool.free", "p");
        target.put("tool.circle", "c");
        target.put("tool.rectangle", "r");
        target.put("tool.ellipse", "e");
        target.put("tool.semicircle", "s");
        target.put("tool.arc", "a");

        target.put("edit.undo", "ctrl+z");
        target.put("edit.redo", "ctrl+y");
        target.put("file.save", "ctrl+s");
        target.put("file.open", "ctrl+o");
        target.put("file.export", "ctrl+e");

        target.put("open.settings", "ctrl+comma");
        target.put("open.keycheatsheet", "f1");
    }

    private Path initConfigPath() {
        try {
            java.nio.file.Path base = java.nio.file.Paths.get(
                    net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toString(),
                    "plot");
            if (!Files.exists(base)) Files.createDirectories(base);
            return base.resolve("keymap.json");
        } catch (Exception e) {
            LogManager.getInstance().error("初始化快捷键配置路径失败", e);
            return null;
        }
    }

    private void loadOrDefault() {
        // 1) 先写入默认值；2) 再用用户配置覆盖默认值
        actionToShortcut.clear();
        setDefaults();
        if (configPath == null || !Files.exists(configPath)) return;

        try (FileReader r = new FileReader(configPath.toFile())) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = gson.fromJson(r, type);
            if (loaded != null) {
                actionToShortcut.putAll(loaded);
            }
        } catch (IOException e) {
            LogManager.getInstance().error("读取快捷键配置失败", e);
        }
    }

    private void save() {
        if (configPath == null) return;
        try (FileWriter w = new FileWriter(configPath.toFile())) { gson.toJson(actionToShortcut, w); }
        catch (IOException e) { LogManager.getInstance().error("保存快捷键配置失败", e); }
    }
}

class UIShortcutActions {
    static boolean dispatch(String actionId) {
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

    static class UiActions {
        static boolean openSaveDialog() {
            try {
                com.plot.ui.dialog.SaveFileDialog d = new com.plot.ui.dialog.SaveFileDialog(
                        com.plot.core.state.AppState.getInstance(),
                        com.plot.infrastructure.event.EventBus.getInstance(), null);
                d.show();
                return true;
            } catch (Exception e) { return false; }
        }
        static boolean openImportDialog() {
            try {
                com.plot.ui.dialog.ImportFileDialog d = new com.plot.ui.dialog.ImportFileDialog(
                        com.plot.core.state.AppState.getInstance(),
                        com.plot.infrastructure.event.EventBus.getInstance(), null);
                d.show();
                return true;
            } catch (Exception e) { return false; }
        }
        static boolean openExportDialog() { return false; }
        static boolean publishUndo() {
            com.plot.infrastructure.event.EventBus.getInstance().publish(
                    new com.plot.infrastructure.event.command.UndoEvent());
            return true;
        }
        static boolean publishRedo() {
            com.plot.infrastructure.event.EventBus.getInstance().publish(
                    new com.plot.infrastructure.event.command.RedoEvent());
            return true;
        }
        static boolean openSettings() {
            com.plot.ui.dialog.SettingsAndHelpDialog.getInstance().open();
            return true;
        }
        static boolean openCheatSheet() { return false; }
    }

    static class ToolActions {
        static boolean activate(String toolId) {
            try {
                com.plot.core.tool.ToolManager toolManager = com.plot.core.tool.ToolManager.getInstance();
                com.plot.api.tool.ITool tool = toolManager.getTool(toolId);
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


