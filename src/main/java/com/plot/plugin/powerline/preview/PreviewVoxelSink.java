package com.plot.plugin.powerline.preview;

import com.plot.core.block.BlockSpec;
import com.plot.plugin.powerline.placement.VoxelSink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将体素写入内存列表的 {@link VoxelSink} 实现。 */
public final class PreviewVoxelSink implements VoxelSink {
    private final Map<String, String> blocks = new LinkedHashMap<>();

    @Override
    public void put(int x, int y, int z, String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return;
        }
        blocks.put(key(x, y, z), blockId);
    }

    @Override
    public void put(int x, int y, int z, BlockSpec block) {
        if (block == null) {
            return;
        }
        blocks.put(key(x, y, z), block.toSetBlockArgument());
    }

    public List<PreviewVoxel> snapshot() {
        List<PreviewVoxel> voxels = new ArrayList<>(blocks.size());
        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            String[] parts = entry.getKey().split(",");
            voxels.add(new PreviewVoxel(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                entry.getValue()));
        }
        return voxels;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
