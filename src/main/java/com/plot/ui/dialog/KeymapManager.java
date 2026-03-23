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
        public ActionDef(String actionId, String displayName) {
            this.actionId = actionId; this.displayName = displayName;
        }
        public String actionId() { return actionId; }
        public String displayName() { return displayName; }
    }

    private static final KeymapManager INSTANCE = new KeymapManager();
    private final Map<String, String> actionToShortcut = new LinkedHashMap<>();
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

    public boolean updateBinding(String actionId, String shortcut) {
        if (actionId == null || shortcut == null) return false;
        String normalized = shortcut.toLowerCase(Locale.ROOT);
        // 简单冲突：移除旧动作占用
        String conflicted = findActionByShortcut(normalized);
        if (conflicted != null && !conflicted.equals(actionId)) actionToShortcut.remove(conflicted);
        actionToShortcut.put(actionId, normalized);
        save();
        return true;
    }

    public void clearBinding(String actionId) { actionToShortcut.remove(actionId); save(); }
    public void resetToDefault() { actionToShortcut.clear(); setDefaults(); save(); }
    public void exportBindings() { save(); }
    public void importBindings() { loadOrDefault(); }

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
        actionToShortcut.put("tool.select", "space");
        actionToShortcut.put("tool.eraser", "d");
        actionToShortcut.put("tool.line", "l");
        actionToShortcut.put("tool.free", "p");
        actionToShortcut.put("tool.circle", "c");
        actionToShortcut.put("tool.rectangle", "r");
        actionToShortcut.put("tool.ellipse", "e");
        actionToShortcut.put("tool.semicircle", "s");
        actionToShortcut.put("tool.arc", "a");

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
        if (configPath == null || !Files.exists(configPath)) { setDefaults(); return; }
        try (FileReader r = new FileReader(configPath.toFile())) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = gson.fromJson(r, type);
            actionToShortcut.clear();
            if (loaded != null) actionToShortcut.putAll(loaded);
            // 合并默认项
            Map<String, String> defaults = new LinkedHashMap<>();
            // temp map
            defaults.put("tool.select", "space"); // sentinel to init
            actionToShortcut.forEach((k,v)->{}); // no-op to avoid lint
            actionToShortcut.clear();
            setDefaults();
            defaults.clear(); defaults.putAll(actionToShortcut);
            actionToShortcut.clear();
            if (loaded != null) actionToShortcut.putAll(defaults);
            if (loaded != null) actionToShortcut.putAll(loaded);
        } catch (IOException e) {
            LogManager.getInstance().error("读取快捷键配置失败，使用默认值", e);
            setDefaults();
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

    static class UiActions {
        static boolean openNewDialog() {
            try {
                com.plot.ui.dialog.NewFileDialog d = new com.plot.ui.dialog.NewFileDialog(
                        com.plot.core.state.AppState.getInstance(),
                        com.plot.infrastructure.event.EventBus.getInstance(), null);
                d.show();
                return true;
            } catch (Exception e) { return false; }
        }
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


