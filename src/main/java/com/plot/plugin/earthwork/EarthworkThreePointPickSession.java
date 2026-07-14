package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.state.AppState;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.ui.canvas.Canvas;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.List;

/**
 * 三点定面控制点拾取：在画布上单击，投影到 Minecraft 并采样实面标高。
 */
public final class EarthworkThreePointPickSession {

    public enum Result {
        NONE,
        PICKED,
        OUTSIDE_REGION,
        WORLD_UNAVAILABLE,
        CANCELLED
    }

    public record PickResult(Vec2d canvasPoint, int elevation) {
    }

    public static final class Outcome {
        private final Result result;
        private final int controlPointIndex;
        private final PickResult pick;

        private Outcome(Result result, int controlPointIndex, PickResult pick) {
            this.result = result;
            this.controlPointIndex = controlPointIndex;
            this.pick = pick;
        }

        public static Outcome none() {
            return new Outcome(Result.NONE, -1, null);
        }

        public static Outcome picked(int controlPointIndex, PickResult pick) {
            return new Outcome(Result.PICKED, controlPointIndex, pick);
        }

        public static Outcome failed(Result result, int controlPointIndex) {
            return new Outcome(result, controlPointIndex, null);
        }

        public Result getResult() {
            return result;
        }

        public int getControlPointIndex() {
            return controlPointIndex;
        }

        public PickResult getPick() {
            return pick;
        }
    }

    private boolean active;
    private int controlPointIndex = -1;

    public boolean isActive() {
        return active;
    }

    public int getControlPointIndex() {
        return controlPointIndex;
    }

    public void begin(int controlPointIndex) {
        this.controlPointIndex = controlPointIndex;
        this.active = controlPointIndex >= 0 && controlPointIndex <= 2;
        EarthworkPickGuard.setActive(this.active);
    }

    public void cancel() {
        active = false;
        controlPointIndex = -1;
        EarthworkPickGuard.setActive(false);
    }

    public Outcome tick(AppState appState, List<Vec2d> regionOuterPoints) {
        if (!active || controlPointIndex < 0) {
            return Outcome.none();
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            int index = controlPointIndex;
            cancel();
            return Outcome.failed(Result.CANCELLED, index);
        }

        if (ImGui.getIO().getWantCaptureMouse()) {
            return Outcome.none();
        }

        if (!ImGui.isMouseClicked(0)) {
            return Outcome.none();
        }

        if (appState == null) {
            return Outcome.none();
        }

        Canvas canvas = appState.getCanvas();
        if (canvas == null) {
            return Outcome.none();
        }

        Vec2d screenPos = new Vec2d(ImGui.getMousePosX(), ImGui.getMousePosY());
        if (!canvas.isScreenPosInsideCanvas(screenPos)) {
            return Outcome.none();
        }

        Vec2d canvasPoint = canvas.screenToWorld(screenPos);
        if (regionOuterPoints != null && regionOuterPoints.size() >= 3) {
            Polygon polygon = EarthworkGeometryUtils.toPolygon(regionOuterPoints);
            if (!polygon.contains(canvasPoint)) {
                return Outcome.failed(Result.OUTSIDE_REGION, controlPointIndex);
            }
        }

        MinecraftClient client = MinecraftClient.getInstance();
        World world = client != null ? client.world : null;
        CoordinateTransformer transformer = CoordinateTransformer.getInstance();
        if (world == null || transformer == null) {
            return Outcome.failed(Result.WORLD_UNAVAILABLE, controlPointIndex);
        }

        int elevation = TerrainSurfaceSampler.sampleAtCanvas(world, canvasPoint, transformer);
        int index = controlPointIndex;
        PickResult pick = new PickResult(canvasPoint, elevation);
        cancel();
        return Outcome.picked(index, pick);
    }
}
