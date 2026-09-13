package com.plot.plugin.powerline;

import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.tool.BaseTool;
import imgui.ImGui;
import imgui.flag.ImGuiKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private List<Shape> previousCanvasSelection = List.of();
    private boolean selectionSnapshotInitialized;

    public boolean isActive() {
        return active;
    }

    public int getAccumulatedCount() {
        return accumulatedPaths.size();
    }

    public void begin() {
        active = true;
        accumulatedPaths.clear();
        previousCanvasSelection = List.of();
        selectionSnapshotInitialized = false;
    }

    public void cancel() {
        active = false;
        accumulatedPaths.clear();
        previousCanvasSelection = List.of();
        selectionSnapshotInitialized = false;
    }

    public Outcome tick(AppState appState) {
        if (!active) {
            return Outcome.none();
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            active = false;
            accumulatedPaths.clear();
            previousCanvasSelection = List.of();
            selectionSnapshotInitialized = false;
            return Outcome.failed(Result.CANCELLED);
        }

        if (!selectionSnapshotInitialized) {
            previousCanvasSelection = List.copyOf(appState.getSelectedShapes());
            selectionSnapshotInitialized = true;
        }

        BaseTool tool = appState.getCurrentTool();
        if (tool != null && "select".equals(tool.getId())
                && ImGui.isMouseReleased(0)
                && !ImGui.getIO().getWantCaptureMouse()) {
            mergeSelectionChange(
                previousCanvasSelection,
                appState.getSelectedShapes(),
                ImGui.getIO().getKeyCtrl());
        }

        previousCanvasSelection = List.copyOf(appState.getSelectedShapes());

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
        previousCanvasSelection = List.of();
        selectionSnapshotInitialized = false;
        appState.setSelectedShapes(paths);
        return Outcome.success(paths);
    }

    public String hintKeyForCurrentSelection(List<Shape> selected) {
        int count = accumulatedPaths.isEmpty()
            ? PowerLinePathUtils.findAdoptableLines(selected).size()
            : accumulatedPaths.size();
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

    void mergeSelectionChange(List<Shape> previousCanvas, List<Shape> currentCanvas, boolean ctrlToggle) {
        SelectionDelta delta = SelectionDelta.between(previousCanvas, currentCanvas);
        if (delta.isEmpty()) {
            return;
        }

        if (ctrlToggle) {
            toggleAdoptablePaths(delta.added());
            toggleAdoptablePaths(delta.removed());
            return;
        }

        unionAdoptablePaths(delta.added());
    }

    List<String> accumulatedPathIds() {
        return List.copyOf(accumulatedPaths.keySet());
    }

    private void unionAdoptablePaths(List<Shape> shapes) {
        for (Shape path : PowerLinePathUtils.findAdoptableLines(shapes)) {
            accumulatedPaths.put(path.getId(), path);
        }
    }

    private void toggleAdoptablePaths(List<Shape> shapes) {
        for (Shape path : PowerLinePathUtils.findAdoptableLines(shapes)) {
            if (accumulatedPaths.containsKey(path.getId())) {
                accumulatedPaths.remove(path.getId());
            } else {
                accumulatedPaths.put(path.getId(), path);
            }
        }
    }

    private static final class SelectionDelta {
        private final List<Shape> added;
        private final List<Shape> removed;

        private SelectionDelta(List<Shape> added, List<Shape> removed) {
            this.added = added;
            this.removed = removed;
        }

        static SelectionDelta between(List<Shape> previousCanvas, List<Shape> currentCanvas) {
            Set<String> previousIds = shapeIds(previousCanvas);
            Set<String> currentIds = shapeIds(currentCanvas);

            List<Shape> added = new ArrayList<>();
            for (Shape shape : currentCanvas) {
                if (!previousIds.contains(shape.getId())) {
                    added.add(shape);
                }
            }

            List<Shape> removed = new ArrayList<>();
            for (Shape shape : previousCanvas) {
                if (!currentIds.contains(shape.getId())) {
                    removed.add(shape);
                }
            }

            return new SelectionDelta(added, removed);
        }

        private static Set<String> shapeIds(List<Shape> shapes) {
            Set<String> ids = new HashSet<>();
            for (Shape shape : shapes) {
                ids.add(shape.getId());
            }
            return ids;
        }

        List<Shape> added() {
            return added;
        }

        List<Shape> removed() {
            return removed;
        }

        boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty();
        }
    }
}
