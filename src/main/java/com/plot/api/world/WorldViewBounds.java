package com.plot.api.world;

/**
 * Minecraft 世界 XZ 视野包围盒（画布投影可见范围）。
 */
public record WorldViewBounds(double minX, double maxX, double minZ, double maxZ) {
    public boolean containsBox(int boxMinX, int boxMaxX, int boxMinZ, int boxMaxZ) {
        return boxMinX >= minX && boxMaxX <= maxX && boxMinZ >= minZ && boxMaxZ <= maxZ;
    }

    public boolean intersectsBox(int boxMinX, int boxMaxX, int boxMinZ, int boxMaxZ) {
        return !(boxMaxX < minX || boxMinX > maxX || boxMaxZ < minZ || boxMinZ > maxZ);
    }
}
