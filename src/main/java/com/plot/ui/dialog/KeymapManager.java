package com.plot.ui.dialog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.plot.api.shortcut.IShortcutListener;
import com.plot.core.log.LogManager;
import com.plot.core.shortcut.ShortcutManager;
import com.plot.utils.PlotI18n;

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
        public String displayName() { return PlotI18n.tr(displayName); }
        public String category() { return PlotI18n.tr(category); }
        public String displayNameKey() { return displayName; }
        public String categoryKey() { return category; }
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
        actions.add(new ActionDef("tool.select", "shortcut.plot.action.tool.select", "shortcut.plot.category.drawing"));
        actions.add(new ActionDef("tool.eraser", "shortcut.plot.action.tool.eraser", "shortcut.plot.category.drawing"));
        actions.add(new ActionDef("tool.line", "shortcut.plot.action.tool.line", "shortcut.plot.category.drawing"));
        actions.add(new ActionDef("tool.free", "shortcut.plot.action.tool.free", "shortcut.plot.category.drawing"));
        actions.add(new ActionDef("tool.circle", "shortcut.plot.action.tool.circle", "shortcut.plot.category.drawing"));
        actions.add(new ActionDef("tool.rectangle", "shortcut.plot.action.tool.rectangle", "shortcut.plot.category.drawing"));
        actions.add(new ActionDef("tool.ellipse", "shortcut.plot.action.tool.ellipse", "shortcut.plot.category.drawing"));
        actions.add(new ActionDef("tool.semicircle", "shortcut.plot.action.tool.semicircle", "shortcut.plot.category.drawing"));
        actions.add(new ActionDef("tool.arc", "shortcut.plot.action.tool.arc", "shortcut.plot.category.drawing"));

        // 全局编辑
        actions.add(new ActionDef("edit.undo", "shortcut.plot.action.edit.undo", "shortcut.plot.category.edit"));
        actions.add(new ActionDef("edit.redo", "shortcut.plot.action.edit.redo", "shortcut.plot.category.edit"));
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
                pruneUnknownBindings();
            }
        } catch (IOException e) {
            LogManager.getInstance().error("读取快捷键配置失败", e);
        }
    }

    private void pruneUnknownBindings() {
        Set<String> validActionIds = new HashSet<>();
        for (ActionDef action : actions) {
            validActionIds.add(action.actionId());
        }
        actionToShortcut.entrySet().removeIf(entry -> !validActionIds.contains(entry.getKey()));
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
            case "edit.undo" -> UiActions.publishUndo();
            case "edit.redo" -> UiActions.publishRedo();
            default -> false;
        };
    }

    static class UiActions {
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


