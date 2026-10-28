package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
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
 * 分区/排除区孔洞拾取：在画布上点选封闭图形（普通画布图形，非土方分区），右键确认。
 */
public final class EarthworkHolePickSession {

    public enum TargetKind {
        REGION,
        EXCLUSION
    }

    public enum Result {
        NONE,
        SUCCESS,
        NEED_SELECTION,
        NO_VALID,
        CANCELLED
    }

    public static final class Outcome {
        private final Result result;
        private final TargetKind targetKind;
        private final String targetId;
        private final List<Shape> shapes;

        private Outcome(Result result, TargetKind targetKind, String targetId, List<Shape> shapes) {
            this.result = result;
            this.targetKind = targetKind;
            this.targetId = targetId;
            this.shapes = shapes;
        }

        public static Outcome none() {
            return new Outcome(Result.NONE, null, null, List.of());
        }

        public static Outcome success(TargetKind kind, String targetId, List<Shape> shapes) {
            return new Outcome(Result.SUCCESS, kind, targetId, List.copyOf(shapes));
        }

        public static Outcome failed(Result result, TargetKind kind, String targetId) {
            return new Outcome(result, kind, targetId, List.of());
        }

        public Result getResult() {
            return result;
        }

        public TargetKind getTargetKind() {
            return targetKind;
        }

        public String getTargetId() {
            return targetId;
        }

        public List<Shape> getShapes() {
            return shapes;
        }
    }

    private boolean active;
    private TargetKind targetKind = TargetKind.REGION;
    private String targetId = "";
    private final Map<String, Shape> accumulatedShapes = new LinkedHashMap<>();
    private final PathPickSessionSnapshot canvasSnapshot = new PathPickSessionSnapshot();

    public boolean isActive() {
        return active;
    }

    public TargetKind getTargetKind() {
        return targetKind;
    }

    public String getTargetId() {
        return targetId;
    }

    public int getAccumulatedCount() {
        return accumulatedShapes.size();
    }

    public void begin(TargetKind kind, String targetId) {
        if (kind == null || targetId == null || targetId.isBlank()) {
            cancel();
            return;
        }
        this.active = true;
        this.targetKind = kind;
        this.targetId = targetId;
        accumulatedShapes.clear();
        canvasSnapshot.reset();
    }

    public void cancel() {
        active = false;
        targetId = "";
        accumulatedShapes.clear();
        canvasSnapshot.reset();
    }

    public Outcome tick(AppState appState) {
        if (!active) {
            return Outcome.none();
        }

        TargetKind kind = targetKind;
        String id = targetId;

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            cancel();
            return Outcome.failed(Result.CANCELLED, kind, id);
        }

        canvasSnapshot.ensureInitialized(appState);

        BaseTool tool = appState.getCurrentTool();
        if (tool != null && "select".equals(tool.getId())
                && ImGui.isMouseReleased(0)
                && !ImGui.getIO().getWantCaptureMouse()) {
            PathPickSelectionMerge.merge(
                accumulatedShapes,
                canvasSnapshot.previousSelection(),
                appState.getSelectedShapes(),
                ImGui.getIO().getKeyCtrl(),
                EarthworkGeometryUtils::findAdoptableRegions);
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

        List<Shape> shapes = new ArrayList<>(accumulatedShapes.values());
        if (shapes.isEmpty()) {
            List<Shape> selected = appState.getSelectedShapes();
            shapes = EarthworkGeometryUtils.findAdoptableRegions(selected);
            if (shapes.isEmpty()) {
                Result failed = selected == null || selected.isEmpty()
                    ? Result.NEED_SELECTION
                    : Result.NO_VALID;
                return Outcome.failed(failed, kind, id);
            }
        }

        cancel();
        return Outcome.success(kind, id, shapes);
    }

    public String hintKeyForCurrentSelection(List<Shape> selected) {
        int count = accumulatedShapes.isEmpty()
            ? EarthworkGeometryUtils.findAdoptableRegions(selected).size()
            : accumulatedShapes.size();
        if (count >= 1) {
            return "status.plot.earthwork.pick_hole_right_click";
        }
        if (selected != null && !selected.isEmpty()) {
            return "status.plot.earthwork.pick_hole_no_valid";
        }
        return "status.plot.earthwork.pick_hole_active";
    }
}
