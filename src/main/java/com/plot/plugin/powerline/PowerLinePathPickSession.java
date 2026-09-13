package com.plot.plugin.powerline;

import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.tool.BaseTool;
import imgui.ImGui;
import imgui.flag.ImGuiKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电力线路路径拾取会话（插件私有）。
 * <p>
 * 激活选择工具后，在画布上点选/框选路径，右键确认选择。
 */
public final class PowerLinePathPickSession {

    public enum Result {
        NONE,
        SUCCESS,
        NEED_SELECTION,
        NO_VALID,
        CANCELLED
    }

    public static final class Outcome {
        private final Result result;
        private final List<Shape> paths;

        private Outcome(Result result, List<Shape> paths) {
            this.result = result;
            this.paths = paths;
        }

        public static Outcome none() {
            return new Outcome(Result.NONE, List.of());
        }

        public static Outcome success(List<Shape> paths) {
            return new Outcome(Result.SUCCESS, List.copyOf(paths));
        }

        public static Outcome failed(Result result) {
            return new Outcome(result, List.of());
        }

        public Result getResult() {
            return result;
        }

        public List<Shape> getPaths() {
            return paths;
        }
    }

    private boolean active;
    private final Map<String, Shape> accumulatedPaths = new LinkedHashMap<>();

    public boolean isActive() {
        return active;
    }

    public int getAccumulatedCount() {
        return accumulatedPaths.size();
    }

    public void begin() {
        active = true;
        accumulatedPaths.clear();
    }

    public void cancel() {
        active = false;
        accumulatedPaths.clear();
    }

    public Outcome tick(AppState appState) {
        if (!active) {
            return Outcome.none();
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            active = false;
            accumulatedPaths.clear();
            return Outcome.failed(Result.CANCELLED);
        }

        BaseTool tool = appState.getCurrentTool();
        if (tool != null && "select".equals(tool.getId())
                && ImGui.isMouseReleased(0)
                && !ImGui.getIO().getWantCaptureMouse()) {
            mergeCurrentSelection(appState);
        }

        syncSelectionToAccumulated(appState);

        if (!ImGui.isMouseClicked(1)) {
            return Outcome.none();
        }

        if (ImGui.getIO().getWantCaptureMouse()) {
            return Outcome.none();
        }

        if (tool == null || !"select".equals(tool.getId())) {
            return Outcome.none();
        }

        List<Shape> paths = new ArrayList<>(accumulatedPaths.values());
        if (paths.isEmpty()) {
            List<Shape> selected = appState.getSelectedShapes();
            paths = PowerLinePathUtils.findAdoptableLines(selected);
            if (paths.isEmpty()) {
                return Outcome.failed(selected.isEmpty() ? Result.NEED_SELECTION : Result.NO_VALID);
            }
        }

        active = false;
        accumulatedPaths.clear();
        appState.setSelectedShapes(paths);
        return Outcome.success(paths);
    }

    public String hintKeyForCurrentSelection(List<Shape> selected) {
        int count = Math.max(
            accumulatedPaths.size(),
            PowerLinePathUtils.findAdoptableLines(selected).size());
        if (count > 1) {
            return "status.plot.powerline.pick_path_right_click_multi";
        }
        if (count == 1) {
            return "status.plot.powerline.pick_path_right_click";
        }
        if (selected != null && !selected.isEmpty()) {
            return "status.plot.powerline.pick_path_no_valid";
        }
        return "status.plot.powerline.pick_path_active";
    }

    private void mergeCurrentSelection(AppState appState) {
        List<Shape> adoptable = PowerLinePathUtils.findAdoptableLines(appState.getSelectedShapes());
        if (adoptable.isEmpty()) {
            return;
        }

        boolean toggleMode = ImGui.getIO().getKeyCtrl();
        if (toggleMode) {
            for (Shape path : adoptable) {
                if (accumulatedPaths.containsKey(path.getId())) {
                    accumulatedPaths.remove(path.getId());
                } else {
                    accumulatedPaths.put(path.getId(), path);
                }
            }
        } else {
            for (Shape path : adoptable) {
                accumulatedPaths.put(path.getId(), path);
            }
        }
    }

    private void syncSelectionToAccumulated(AppState appState) {
        if (accumulatedPaths.isEmpty()) {
            return;
        }
        appState.setSelectedShapes(new ArrayList<>(accumulatedPaths.values()));
    }
}
