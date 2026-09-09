package com.plot.plugin.powerline.preview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将体素写入内存列表的 {@link VoxelSink} 实现。 */
public final class PreviewVoxelSink implements VoxelSink {
    private final Map<Long, String> blocks = new LinkedHashMap<>();

    @Override
    public void put(int x, int y, int z, String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return;
        }
        blocks.put(pack(x, y, z), blockId);
    }

    public List<PreviewVoxel> snapshot() {
        List<PreviewVoxel> voxels = new ArrayList<>(blocks.size());
        for (Map.Entry<Long, String> entry : blocks.entrySet()) {
            long packed = entry.getKey();
            voxels.add(new PreviewVoxel(
                unpackX(packed),
                unpackY(packed),
                unpackZ(packed),
                entry.getValue()));
        }
        return voxels;
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFL) << 42 | ((long) y & 0xFFFFFL) << 22 | (z & 0x3FFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 42);
    }

    private static int unpackY(long packed) {
        return (int) ((packed >> 22) & 0xFFFFFL);
    }

    private static int unpackZ(long packed) {
        return (int) (packed & 0x3FFFFFL);
    }
}
