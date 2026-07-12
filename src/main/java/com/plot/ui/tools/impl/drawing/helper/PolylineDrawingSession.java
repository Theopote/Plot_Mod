package com.plot.ui.tools.impl.drawing.helper;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 当前活动折线/钢笔绘制会话，用于撤销/重做时恢复临时几何。
 */
public final class PolylineDrawingSession {
    public interface GeometrySink {
        boolean applySnapshot(DrawingGeometrySnapshot snapshot);
    }

    private static final AtomicReference<GeometrySink> ACTIVE = new AtomicReference<>();

    private PolylineDrawingSession() {
    }

    public static void register(GeometrySink sink) {
        ACTIVE.set(sink);
    }

    public static void unregister(GeometrySink sink) {
        ACTIVE.compareAndSet(sink, null);
    }

    public static boolean apply(DrawingGeometrySnapshot snapshot) {
        GeometrySink sink = ACTIVE.get();
        return sink != null && sink.applySnapshot(snapshot);
    }
}
