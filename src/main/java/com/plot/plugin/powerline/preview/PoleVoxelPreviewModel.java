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

    public String blockAtFront(int x, int y) {
        String chosen = null;
        int bestZ = Integer.MIN_VALUE;
        for (PreviewVoxel voxel : voxels) {
            if (voxel.x() == x && voxel.y() == y && voxel.z() >= bestZ) {
                bestZ = voxel.z();
                chosen = voxel.blockId();
            }
        }
        return chosen;
    }

    public String blockAtSide(int z, int y) {
        String chosen = null;
        int bestX = Integer.MIN_VALUE;
        for (PreviewVoxel voxel : voxels) {
            if (voxel.z() == z && voxel.y() == y && voxel.x() >= bestX) {
                bestX = voxel.x();
                chosen = voxel.blockId();
            }
        }
        return chosen;
    }

    static PoleVoxelPreviewModel fromSink(PreviewVoxelSink sink) {
        return new PoleVoxelPreviewModel(new ArrayList<>(sink.snapshot()));
    }
}
