package com.plot.plugin.earthwork.grading;

/**
 * 按列比较现状与设计标高，判定挖/填方量与预览类型。
 */
public final class CutFillClassifier {

    public enum Kind {
        CUT,
        FILL,
        NONE
    }

    public record ColumnDelta(long cutVolume, long fillVolume) {
        public boolean isNoOp() {
            return cutVolume == 0L && fillVolume == 0L;
        }
    }

    private CutFillClassifier() {
    }

    public static ColumnDelta delta(int groundY, int targetElevation) {
        if (groundY > targetElevation) {
            return new ColumnDelta(groundY - targetElevation, 0L);
        }
        if (groundY < targetElevation) {
            return new ColumnDelta(0L, targetElevation - groundY);
        }
        return new ColumnDelta(0L, 0L);
    }

    public static Kind kind(int groundY, int targetElevation) {
        if (groundY > targetElevation) {
            return Kind.CUT;
        }
        if (groundY < targetElevation) {
            return Kind.FILL;
        }
        return Kind.NONE;
    }
}
