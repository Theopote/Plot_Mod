package com.plot.plugin.earthwork;

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
 * 土方整平区域拾取会话（插件私有）。
 * <p>
 * 激活选择工具后，在画布上点选/框选封闭或可由插件自动封闭的轮廓，右键确认。
 */
public final class EarthworkRegionPickSession {

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

    public boolean isActive() {
        return active;
    }

    public int getAccumulatedCount() {
        return accumulatedRegions.size();
    }

    public void begin() {
        active = true;
        accumulatedRegions.clear();
    }

    public void cancel() {
        active = false;
        accumulatedRegions.clear();
    }

    public Outcome tick(AppState appState) {
        if (!active) {
            return Outcome.none();
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            active = false;
            accumulatedRegions.clear();
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

        List<Shape> regions = new ArrayList<>(accumulatedRegions.values());
        if (regions.isEmpty()) {
            List<Shape> selected = appState.getSelectedShapes();
            regions = EarthworkGeometryUtils.findAdoptableRegions(selected);
            if (regions.isEmpty()) {
                return Outcome.failed(selected.isEmpty() ? Result.NEED_SELECTION : Result.NO_VALID);
            }
        }

        active = false;
        accumulatedRegions.clear();
        appState.setSelectedShapes(regions);
        return Outcome.success(regions);
    }

    public String hintKeyForCurrentSelection(List<Shape> selected) {
        int count = Math.max(
            accumulatedRegions.size(),
            EarthworkGeometryUtils.findAdoptableRegions(selected).size());
        if (count > 1) {
            return "status.plot.earthwork.pick_region_right_click_multi";
        }
        if (count == 1) {
            return "status.plot.earthwork.pick_region_right_click";
        }
        if (selected != null && !selected.isEmpty()) {
            return "status.plot.earthwork.pick_region_no_valid";
        }
        return "status.plot.earthwork.pick_region_active";
    }

    private void mergeCurrentSelection(AppState appState) {
        List<Shape> adoptable = EarthworkGeometryUtils.findAdoptableRegions(appState.getSelectedShapes());
        if (adoptable.isEmpty()) {
            return;
        }

        boolean toggleMode = ImGui.getIO().getKeyCtrl();
        if (toggleMode) {
            for (Shape region : adoptable) {
                if (accumulatedRegions.containsKey(region.getId())) {
                    accumulatedRegions.remove(region.getId());
                } else {
                    accumulatedRegions.put(region.getId(), region);
                }
            }
        } else {
            for (Shape region : adoptable) {
                accumulatedRegions.put(region.getId(), region);
            }
        }
    }

    private void syncSelectionToAccumulated(AppState appState) {
        if (accumulatedRegions.isEmpty()) {
            return;
        }
        appState.setSelectedShapes(new ArrayList<>(accumulatedRegions.values()));
    }
}
