package com.plot.plugin.road;

import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.tool.BaseTool;
import imgui.ImGui;
import imgui.flag.ImGuiKey;

import java.util.List;

/**
 * 道路路径拾取会话（插件私有）。
 * <p>
 * 不修改 Plot 核心工具：激活选择工具后，由插件在每帧 render 中监听画布右键以确认拾取。
 */
public class RoadPathPickSession {

    public enum Result {
        NONE,
        SUCCESS,
        NEED_SELECTION,
        NO_VALID,
        CANCELLED
    }

    public static final class Outcome {
        private final Result result;
        private final Shape path;

        private Outcome(Result result, Shape path) {
            this.result = result;
            this.path = path;
        }

        public static Outcome none() {
            return new Outcome(Result.NONE, null);
        }

        public static Outcome success(Shape path) {
            return new Outcome(Result.SUCCESS, path);
        }

        public static Outcome failed(Result result) {
            return new Outcome(result, null);
        }

        public Result getResult() {
            return result;
        }

        public Shape getPath() {
            return path;
        }
    }

    private boolean active;

    public boolean isActive() {
        return active;
    }

    public void begin() {
        active = true;
    }

    public void cancel() {
        active = false;
    }

    /**
     * 每帧调用，检测 Esc 取消与画布右键确认。
     */
    public Outcome tick(AppState appState) {
        if (!active) {
            return Outcome.none();
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            active = false;
            return Outcome.failed(Result.CANCELLED);
        }

        if (!ImGui.isMouseClicked(1)) {
            return Outcome.none();
        }

        if (ImGui.getIO().getWantCaptureMouse()) {
            return Outcome.none();
        }

        BaseTool tool = appState.getCurrentTool();
        if (tool == null || !"select".equals(tool.getId())) {
            return Outcome.none();
        }

        List<Shape> selected = appState.getSelectedShapes();
        Shape path = RoadGeometryUtils.findFirstAdoptablePath(selected);
        if (path == null) {
            return Outcome.failed(selected.isEmpty() ? Result.NEED_SELECTION : Result.NO_VALID);
        }

        active = false;
        appState.setSelectedShapes(List.of(path));
        return Outcome.success(path);
    }

    /**
     * 根据当前选择集生成提示文案（拾取进行中，尚未右键确认）。
     */
    public static String hintForSelection(List<Shape> selected) {
        if (RoadGeometryUtils.findFirstAdoptablePath(selected) != null) {
            return "status.plot.road.pick_path_right_click";
        }
        if (selected != null && !selected.isEmpty()) {
            return "status.plot.road.pick_path_no_valid";
        }
        return "status.plot.road.pick_path_active";
    }
}
