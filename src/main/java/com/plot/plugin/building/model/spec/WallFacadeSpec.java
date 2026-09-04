package com.plot.plugin.building.model.spec;

/**
 * 单段外墙的立面参数（窗型等）。
 * <p>
 * {@link #wallSegmentIndex()} 对应轮廓 {@code outerPoints} 的边索引（0 = 第一条边）。
 */
public final class WallFacadeSpec {
    private final int wallSegmentIndex;
    private final WindowPatternSpec windowPattern;

    public WallFacadeSpec(int wallSegmentIndex, WindowPatternSpec windowPattern) {
        if (wallSegmentIndex < 0) {
            throw new IllegalArgumentException("wallSegmentIndex must be >= 0");
        }
        this.wallSegmentIndex = wallSegmentIndex;
        this.windowPattern = windowPattern != null
            ? windowPattern
            : new WindowPatternSpec(4, 1, 2, 1);
    }

    public static WallFacadeSpec of(int wallSegmentIndex, WindowPatternSpec windowPattern) {
        return new WallFacadeSpec(wallSegmentIndex, windowPattern);
    }

    /** 该墙段不开窗（spacing=0）。 */
    public static WallFacadeSpec noWindows(int wallSegmentIndex) {
        return new WallFacadeSpec(wallSegmentIndex, new WindowPatternSpec(0, 1, 2, 1));
    }

    public int wallSegmentIndex() {
        return wallSegmentIndex;
    }

    public WindowPatternSpec windowPattern() {
        return windowPattern;
    }
}
