package com.plot.plugin.pattern;

import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.tool.BaseTool;
import com.plot.plugin.pick.PathPickSelectionMerge;
import com.plot.plugin.pick.PathPickSessionSnapshot;
import imgui.ImGui;
import imgui.flag.ImGuiKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 铺装区域拾取会话。
 */
public class PatternRegionPickSession {
    public enum Result {
        NONE,
        SUCCESS,
        NEED_SELECTION,
        NO_VALID,
        CANCELLED
    }

    public static final class Outcome {
        private final Result result;
        private final List<Shape> regions;

        private Outcome(Result result, List<Shape> regions) {
            this.result = result;
            this.regions = regions;
        }

        public static Outcome none() {
            return new Outcome(Result.NONE, List.of());
        }

        public static Outcome success(List<Shape> regions) {
            return new Outcome(Result.SUCCESS, List.copyOf(regions));
        }

        public static Outcome failed(Result result) {
            return new Outcome(result, List.of());
        }

        public Result getResult() {
            return result;
        }

        public List<Shape> getRegions() {
            return regions;
        }
    }

    private boolean active;
    private final Map<String, Shape> accumulatedRegions = new LinkedHashMap<>();
    private final PathPickSessionSnapshot canvasSnapshot = new PathPickSessionSnapshot();

    public boolean isActive() {
        return active;
    }

    public int getAccumulatedCount() {
        return accumulatedRegions.size();
    }

    public void begin() {
        active = true;
        accumulatedRegions.clear();
        canvasSnapshot.reset();
    }

    public void cancel() {
        active = false;
        accumulatedRegions.clear();
        canvasSnapshot.reset();
    }

    public Outcome tick(AppState appState) {
        if (!active) {
            return Outcome.none();
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            active = false;
            accumulatedRegions.clear();
            canvasSnapshot.reset();
            return Outcome.failed(Result.CANCELLED);
        }

        canvasSnapshot.ensureInitialized(appState);

        BaseTool tool = appState.getCurrentTool();
        if (tool != null && "select".equals(tool.getId())
                && ImGui.isMouseReleased(0)
                && !ImGui.getIO().getWantCaptureMouse()) {
            PathPickSelectionMerge.merge(
                accumulatedRegions,
                canvasSnapshot.previousSelection(),
                appState.getSelectedShapes(),
                ImGui.getIO().getKeyCtrl(),
                PatternGeometryUtils::findAdoptableRegions);
        }

        canvasSnapshot.capture(appState);

        if (!ImGui.isMouseClicked(1)) {
            return Outcome.none();
        }

        if (ImGui.getIO().getWantCaptureMouse()) {
            return Outcome.none();
        }

        if (tool == null || !"select".equals(tool.getId())) {
            return Outcome.none();
        }

        List<Shape> regions = new ArrayList<>(accumulatedRegions.values());
        if (regions.isEmpty()) {
            List<Shape> selected = appState.getSelectedShapes();
            regions = PatternGeometryUtils.findAdoptableRegions(selected);
            if (regions.isEmpty()) {
                return Outcome.failed(selected.isEmpty() ? Result.NEED_SELECTION : Result.NO_VALID);
            }
        }

        active = false;
        accumulatedRegions.clear();
        canvasSnapshot.reset();
        appState.setSelectedShapes(regions);
        return Outcome.success(regions);
    }

    public String hintKeyForCurrentSelection(List<Shape> selected) {
        int count = accumulatedRegions.isEmpty()
            ? PatternGeometryUtils.findAdoptableRegions(selected).size()
            : accumulatedRegions.size();
        if (count > 1) {
            return "status.plot.pattern.pick_region_right_click_multi";
        }
        if (count == 1) {
            return "status.plot.pattern.pick_region_right_click";
        }
        if (selected != null && !selected.isEmpty()) {
            return "status.plot.pattern.pick_region_no_valid";
        }
        return "status.plot.pattern.pick_region_active";
    }
}
