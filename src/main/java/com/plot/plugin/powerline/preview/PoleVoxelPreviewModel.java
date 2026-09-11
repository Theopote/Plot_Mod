package com.plot.plugin.powerline.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 杆塔局部体素模型，供立面预览渲染。 */
public final class PoleVoxelPreviewModel {
    private final List<PreviewVoxel> voxels;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;

    public PoleVoxelPreviewModel(List<PreviewVoxel> voxels) {
        if (voxels == null || voxels.isEmpty()) {
            this.voxels = List.of();
            minX = maxX = minY = maxY = minZ = maxZ = 0;
            return;
        }
        this.voxels = List.copyOf(voxels);
        int x0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y0 = Integer.MAX_VALUE;
        int y1 = Integer.MIN_VALUE;
        int z0 = Integer.MAX_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (PreviewVoxel voxel : voxels) {
            x0 = Math.min(x0, voxel.x());
            x1 = Math.max(x1, voxel.x());
            y0 = Math.min(y0, voxel.y());
            y1 = Math.max(y1, voxel.y());
            z0 = Math.min(z0, voxel.z());
            z1 = Math.max(z1, voxel.z());
        }
        minX = x0;
        maxX = x1;
        minY = y0;
        maxY = y1;
        minZ = z0;
        maxZ = z1;
    }

    public List<PreviewVoxel> voxels() {
        return voxels;
    }

    public boolean isEmpty() {
        return voxels.isEmpty();
    }

    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    public int widthX() {
        return isEmpty() ? 0 : maxX - minX + 1;
    }

    public int widthZ() {
        return isEmpty() ? 0 : maxZ - minZ + 1;
    }

    public int heightY() {
        return isEmpty() ? 0 : maxY - minY + 1;
    }

    /**
     * 正交立面的第一命中：该像素列上沿视线最近的一块。
     * <p>
     * 正视沿 {@code z} 取最小 z，侧视沿 {@code x} 取最小 x。
     * 不能改成固定 {@code minZ}/{@code minX} 平面：横担沿 X 伸出后，侧视的
     * {@code minX} 是端头，塔身会被切掉；格构塔正视的 {@code minZ} 是前腿，横担和腹杆会消失。
     */
    public String blockAtFront(int x, int y) {
        return nearestAlongView(x, y, true);
    }

    public String blockAtSide(int z, int y) {
        return nearestAlongView(z, y, false);
    }

    private String nearestAlongView(int across, int y, boolean frontView) {
        String chosen = null;
        int bestAlong = Integer.MAX_VALUE;
        for (PreviewVoxel voxel : voxels) {
            if (voxel.y() != y) {
                continue;
            }
            int voxelAcross = frontView ? voxel.x() : voxel.z();
            if (voxelAcross != across) {
                continue;
            }
            int along = frontView ? voxel.z() : voxel.x();
            if (along < bestAlong) {
                bestAlong = along;
                chosen = voxel.blockId();
            }
        }
        return chosen;
    }

    static PoleVoxelPreviewModel fromSink(PreviewVoxelSink sink) {
        return new PoleVoxelPreviewModel(new ArrayList<>(sink.snapshot()));
    }
}
