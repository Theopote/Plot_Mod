package com.plot.plugin.building;

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
 * 建筑轮廓拾取会话（插件私有）
 */
public class BuildingFootprintPickSession {

    public enum Result {
        NONE,
        SUCCESS,
        NEED_SELECTION,
        NO_VALID,
        CANCELLED
    }

    public static final class Outcome {
        private final Result result;
        private final List<Shape> footprints;

        private Outcome(Result result, List<Shape> footprints) {
            this.result = result;
            this.footprints = footprints;
        }

        public static Outcome none() {
            return new Outcome(Result.NONE, List.of());
        }

        public static Outcome success(List<Shape> footprints) {
            return new Outcome(Result.SUCCESS, List.copyOf(footprints));
        }

        public static Outcome failed(Result result) {
            return new Outcome(result, List.of());
        }

        public Result getResult() {
            return result;
        }

        public List<Shape> getFootprints() {
            return footprints;
        }
    }

    private boolean active;
    private final Map<String, Shape> accumulatedFootprints = new LinkedHashMap<>();

    public boolean isActive() {
        return active;
    }

    public int getAccumulatedCount() {
        return accumulatedFootprints.size();
    }

    public void begin() {
        active = true;
        accumulatedFootprints.clear();
    }

    public void cancel() {
        active = false;
        accumulatedFootprints.clear();
    }

    public Outcome tick(AppState appState) {
        if (!active) {
            return Outcome.none();
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            active = false;
            accumulatedFootprints.clear();
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

        List<Shape> footprints = new ArrayList<>(accumulatedFootprints.values());
        if (footprints.isEmpty()) {
            List<Shape> selected = appState.getSelectedShapes();
            footprints = BuildingGeometryUtils.findAdoptableFootprints(selected);
            if (footprints.isEmpty()) {
                return Outcome.failed(selected.isEmpty() ? Result.NEED_SELECTION : Result.NO_VALID);
            }
        }

        active = false;
        accumulatedFootprints.clear();
        appState.setSelectedShapes(footprints);
        return Outcome.success(footprints);
    }

    public String hintKeyForCurrentSelection(List<Shape> selected) {
        int count = Math.max(
            accumulatedFootprints.size(),
            BuildingGeometryUtils.findAdoptableFootprints(selected).size());
        if (count > 1) {
            return "status.plot.building.pick_footprint_right_click_multi";
        }
        if (count == 1) {
            return "status.plot.building.pick_footprint_right_click";
        }
        if (selected != null && !selected.isEmpty()) {
            return "status.plot.building.pick_footprint_no_valid";
        }
        return "status.plot.building.pick_footprint_active";
    }

    private void mergeCurrentSelection(AppState appState) {
        List<Shape> adoptable = BuildingGeometryUtils.findAdoptableFootprints(appState.getSelectedShapes());
        if (adoptable.isEmpty()) {
            return;
        }

        boolean toggleMode = ImGui.getIO().getKeyCtrl();
        if (toggleMode) {
            for (Shape footprint : adoptable) {
                if (accumulatedFootprints.containsKey(footprint.getId())) {
                    accumulatedFootprints.remove(footprint.getId());
                } else {
                    accumulatedFootprints.put(footprint.getId(), footprint);
                }
            }
        } else {
            for (Shape footprint : adoptable) {
                accumulatedFootprints.put(footprint.getId(), footprint);
            }
        }
    }

    private void syncSelectionToAccumulated(AppState appState) {
        if (accumulatedFootprints.isEmpty()) {
            return;
        }
        appState.setSelectedShapes(new ArrayList<>(accumulatedFootprints.values()));
    }
}
