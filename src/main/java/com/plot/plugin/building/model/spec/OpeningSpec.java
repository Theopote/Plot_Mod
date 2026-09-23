package com.plot.plugin.building.model.spec;

/**
 * 统一的立面开洞定义：窗、门、拱洞等共享同一套定位参数。
 */
public final class OpeningSpec {
    private final OpeningKind kind;
    private final int wallSegmentIndex;
    private final double positionRatio;
    private final int floor;
    private final int width;
    private final int height;
    /** 门/拱：相对楼板方块的底边偏移；窗：相对楼板上表面的窗台高度（0 = 贴在楼板顶面）。 */
    private final int bottomOffset;

    public OpeningSpec(
            OpeningKind kind,
            int wallSegmentIndex,
            double positionRatio,
            int floor,
            int width,
            int height,
            int bottomOffset) {
        this.kind = kind != null ? kind : OpeningKind.DOOR;
        this.wallSegmentIndex = wallSegmentIndex;
        this.positionRatio = positionRatio;
        this.floor = floor;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.bottomOffset = Math.max(0, bottomOffset);
    }

    public static OpeningSpec door(
            int wallSegmentIndex,
            double positionRatio,
            int floor,
            int width,
            int height) {
        return new OpeningSpec(
            OpeningKind.DOOR, wallSegmentIndex, positionRatio, floor, width, height, 0);
    }

    public static OpeningSpec window(
            int wallSegmentIndex,
            double positionRatio,
            int floor,
            int width,
            int height,
            int sillHeight) {
        return new OpeningSpec(
            OpeningKind.WINDOW, wallSegmentIndex, positionRatio, floor, width, height, sillHeight);
    }

    public static OpeningSpec arch(
            int wallSegmentIndex,
            double positionRatio,
            int floor,
            int width,
            int height) {
        return new OpeningSpec(
            OpeningKind.ARCH, wallSegmentIndex, positionRatio, floor, width, height, 0);
    }

    public OpeningKind kind() {
        return kind;
    }

    public int wallSegmentIndex() {
        return wallSegmentIndex;
    }

    public double positionRatio() {
        return positionRatio;
    }

    public int floor() {
        return floor;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int bottomOffset() {
        return bottomOffset;
    }
}
