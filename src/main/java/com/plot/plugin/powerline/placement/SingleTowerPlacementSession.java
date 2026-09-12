package com.plot.plugin.powerline.placement;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.state.AppState;
import com.plot.core.terrain.MinecraftTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.canvas.CanvasAccess;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

/**
 * 单塔放置会话：画布悬停跟随 + 正交旋转 + 左键确认（骨架阶段不写入世界）。
 */
public final class SingleTowerPlacementSession {

    public enum Phase {
        IDLE,
        PLACING
    }

    public enum Result {
        NONE,
        PLACED,
        CANCELLED,
        WORLD_UNAVAILABLE
    }

    public static final class Outcome {
        private final Result result;
        private final Vec2d planPoint;
        private final int rotationQuadrant;

        private Outcome(Result result, Vec2d planPoint, int rotationQuadrant) {
            this.result = result;
            this.planPoint = planPoint;
            this.rotationQuadrant = rotationQuadrant;
        }

        public static Outcome none() {
            return new Outcome(Result.NONE, null, 0);
        }

        public static Outcome placed(Vec2d planPoint, int rotationQuadrant) {
            return new Outcome(Result.PLACED, planPoint != null ? planPoint.copy() : null, rotationQuadrant);
        }

        public static Outcome failed(Result result) {
            return new Outcome(result, null, 0);
        }

        public Result getResult() {
            return result;
        }

        public Vec2d getPlanPoint() {
            return planPoint;
        }

        public int getRotationQuadrant() {
            return rotationQuadrant;
        }
    }

    private Phase phase = Phase.IDLE;
    private PowerLineFootprint styleSource;
    private PoleDesign design;
    private String designLabel = "";
    private Vec2d hoverPlanPoint;
    private int hoverBuildBaseY;
    private boolean hoverValid;
    private int rotationQuadrant;
    private boolean rotateKeyWasDown;

    public Phase getPhase() {
        return phase;
    }

    public boolean isActive() {
        return phase == Phase.PLACING;
    }

    public void begin(PoleDesign design, String designLabel, PowerLineFootprint styleSource) {
        this.design = design;
        this.designLabel = designLabel != null ? designLabel : "";
        this.styleSource = styleSource;
        this.hoverPlanPoint = null;
        this.hoverBuildBaseY = 0;
        this.hoverValid = false;
        this.rotationQuadrant = 0;
        this.rotateKeyWasDown = false;
        this.phase = design != null && styleSource != null ? Phase.PLACING : Phase.IDLE;
        SingleTowerPickGuard.setActive(isActive());
    }

    public void cancel() {
        phase = Phase.IDLE;
        styleSource = null;
        design = null;
        designLabel = "";
        hoverPlanPoint = null;
        hoverValid = false;
        rotateKeyWasDown = false;
        SingleTowerPickGuard.setActive(false);
    }

    public SingleTowerPlacementState snapshot() {
        if (!isActive() || design == null) {
            return null;
        }
        return new SingleTowerPlacementState(
            hoverPlanPoint != null ? hoverPlanPoint.copy() : null,
            hoverBuildBaseY,
            rotationQuadrant,
            design,
            designLabel,
            hoverValid);
    }

    public PowerLineFootprint styleSource() {
        return styleSource;
    }

    public Outcome tick(AppState appState, ICoordinateService coordinates, Runnable onHoverChanged) {
        if (!isActive()) {
            return Outcome.none();
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            cancel();
            return Outcome.failed(Result.CANCELLED);
        }

        if (rotateKeyPressedThisFrame()) {
            rotationQuadrant = SingleTowerOrientation.rotateClockwise(rotationQuadrant);
            if (onHoverChanged != null) {
                onHoverChanged.run();
            }
        }

        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel != 0f) {
            rotationQuadrant = SingleTowerOrientation.fromWheelDelta(rotationQuadrant, wheel);
            if (onHoverChanged != null) {
                onHoverChanged.run();
            }
        }

        World world = MinecraftClient.getInstance() != null
            ? MinecraftClient.getInstance().world
            : null;
        if (world == null) {
            hoverValid = false;
            return Outcome.none();
        }

        boolean hoverUpdated = updateHoverFromMouse(
            appState,
            coordinates != null
                ? MinecraftTerrainSampler.of(world, coordinates)
                : null);
        if (hoverUpdated && onHoverChanged != null) {
            onHoverChanged.run();
        }

        if (ImGui.getIO().getWantCaptureMouse()) {
            return Outcome.none();
        }

        if (ImGui.isMouseClicked(0) && hoverValid && hoverPlanPoint != null) {
            Vec2d placed = hoverPlanPoint.copy();
            int rotation = rotationQuadrant;
            cancel();
            return Outcome.placed(placed, rotation);
        }

        return Outcome.none();
    }

    private boolean rotateKeyPressedThisFrame() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            rotateKeyWasDown = false;
            return false;
        }
        long handle = client.getWindow().getHandle();
        boolean down = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        boolean edge = down && !rotateKeyWasDown;
        rotateKeyWasDown = down;
        return edge;
    }

    private boolean updateHoverFromMouse(AppState appState, TerrainSampler terrain) {
        if (appState == null) {
            hoverValid = false;
            return false;
        }
        if (terrain == null) {
            hoverValid = false;
            return hoverPlanPoint != null;
        }
        if (ImGui.getIO().getWantCaptureMouse()) {
            return false;
        }

        Canvas canvas = CanvasAccess.get();
        if (canvas == null) {
            hoverValid = false;
            return false;
        }

        Vec2d screenPos = new Vec2d(ImGui.getMousePosX(), ImGui.getMousePosY());
        if (!canvas.isScreenPosInsideCanvas(screenPos)) {
            hoverValid = false;
            return hoverPlanPoint != null;
        }

        Vec2d planPoint = canvas.screenToWorld(screenPos);
        int buildBaseY = PolePlacementBase.resolve(planPoint, terrain).buildBaseY();
        boolean changed = hoverPlanPoint == null
            || !hoverPlanPoint.equals(planPoint)
            || buildBaseY != hoverBuildBaseY;
        hoverPlanPoint = planPoint.copy();
        hoverBuildBaseY = buildBaseY;
        hoverValid = true;
        return changed;
    }
}
