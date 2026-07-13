package com.plot.ui.panel.gallery;

import com.plot.api.geometry.Vec2d;
import com.plot.core.gallery.GalleryItem;
import com.plot.core.gallery.GalleryRepository;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.Events;
import com.plot.ui.canvas.Canvas;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiKey;

/**
 * 图库放置会话：在画布上点击以放置图库图形。
 */
public final class GalleryPlaceSession {

    public enum Result {
        NONE,
        PLACED,
        CANCELLED
    }

    public static final class Outcome {
        private final Result result;
        private final GalleryItem item;

        private Outcome(Result result, GalleryItem item) {
            this.result = result;
            this.item = item;
        }

        public static Outcome none() {
            return new Outcome(Result.NONE, null);
        }

        public static Outcome placed(GalleryItem item) {
            return new Outcome(Result.PLACED, item);
        }

        public static Outcome cancelled() {
            return new Outcome(Result.CANCELLED, null);
        }

        public Result getResult() {
            return result;
        }

        public GalleryItem getItem() {
            return item;
        }
    }

    private boolean active;
    private GalleryItem pendingItem;

    public boolean isActive() {
        return active;
    }

    public GalleryItem getPendingItem() {
        return pendingItem;
    }

    public void begin(GalleryItem item) {
        this.pendingItem = item;
        this.active = item != null;
        GalleryPlacementGuard.setActive(active);
        if (active) {
            publishStatus("status.plot.gallery.place_active", item.getDisplayName());
        }
    }

    public void cancel() {
        active = false;
        pendingItem = null;
        GalleryPlacementGuard.setActive(false);
        publishStatus("status.plot.gallery.place_cancelled");
    }

    public Outcome tick(AppState appState) {
        if (!active || pendingItem == null || appState == null) {
            return Outcome.none();
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            cancel();
            return Outcome.cancelled();
        }

        if (ImGui.getIO().getWantCaptureMouse()) {
            return Outcome.none();
        }

        if (!ImGui.isMouseClicked(0)) {
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

        Vec2d worldPos = canvas.screenToWorld(screenPos);
        GalleryRepository.getInstance().placeOnCanvas(pendingItem, worldPos, appState);
        GalleryItem placedItem = pendingItem;
        active = false;
        pendingItem = null;
        GalleryPlacementGuard.setActive(false);
        return Outcome.placed(placedItem);
    }

    private static void publishStatus(String key, Object... args) {
        EventBus.getInstance().publish(new Events.StatusMessageEvent(PlotI18n.tr(key, args)));
    }
}
