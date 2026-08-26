package com.plot.core.state;

/**
 * 视口相关应用态（画布透明度等）。缩放由 CanvasCamera 负责，不在此重复。
 */
public final class ViewportState {
    private volatile float opacity;
    private final Object opacityLock = new Object();

    public ViewportState() {
        this.opacity = 0.0f;
    }

    public void setOpacity(float opacity) {
        synchronized (opacityLock) {
            this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        }
    }

    public float getOpacity() {
        synchronized (opacityLock) {
            return opacity;
        }
    }
}
