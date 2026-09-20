package com.plot.plugin.powerline.placement;

import com.plot.api.world.IBlockProjectionService;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.design.BundleVisual;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 导线路径体素放置：链子/避雷针等沿 6-连通路径首尾相接。 */
public final class WireBlockPlacement {
    private WireBlockPlacement() {
    }

    public static void appendConnected(List<BlockPos> path, List<BlockPos> segment) {
        if (segment == null || segment.isEmpty()) {
            return;
        }
        if (path == null) {
            throw new IllegalArgumentException("path required");
        }
        if (path.isEmpty()) {
            path.addAll(segment);
            return;
        }
        BlockPos last = path.get(path.size() - 1);
        int start = segment.get(0).equals(last) ? 1 : 0;
        path.addAll(segment.subList(start, segment.size()));
    }

    public static Map<BlockPos, Integer> indexPath(List<BlockPos> path) {
        Map<BlockPos, Integer> indices = new HashMap<>();
        if (path == null) {
            return indices;
        }
        for (int i = 0; i < path.size(); i++) {
            indices.putIfAbsent(path.get(i), i);
        }
        return indices;
    }

    public static void placeAlongPath(
            MaterialMix material,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            List<BlockPos> path,
            PlacementCategory category) {
        if (path == null || path.isEmpty()) {
            return;
        }
        for (int i = 0; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            String blockId = MaterialMixResolver.resolve(material, pos, footprint.getId());
            String placementId = DirectionalBlockSpecs.resolveWirePlacementAlongPath(blockId, path, i)
                .toSetBlockArgument();
            PlacementWriter.put(result, projection, pos, placementId, category);
        }
    }

    public static void placeBundleSegment(
            MaterialMix material,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            List<BlockPos> segmentPath,
            Map<BlockPos, Integer> spanPathIndex,
            List<BlockPos> spanPath,
            double tangentX,
            double tangentY,
            double tangentZ,
            BundleVisual bundleVisual,
            PlacementCategory category) {
        if (segmentPath == null || segmentPath.isEmpty()) {
            return;
        }
        if (bundleVisual == null || bundleVisual == BundleVisual.SINGLE) {
            for (int j = 0; j < segmentPath.size(); j++) {
                placeOrientedBlock(
                    material,
                    footprint,
                    result,
                    projection,
                    segmentPath.get(j),
                    orientationPath(spanPath, spanPathIndex, segmentPath, j),
                    orientationIndex(spanPathIndex, segmentPath, spanPath, j),
                    category);
            }
            return;
        }
        for (int j = 0; j < segmentPath.size(); j++) {
            BlockPos center = segmentPath.get(j);
            int pathIndex = orientationIndex(spanPathIndex, segmentPath, spanPath, j);
            for (BlockPos pos : bundleVisual.expand(center, tangentX, tangentY, tangentZ)) {
                placeOrientedBlock(
                    material,
                    footprint,
                    result,
                    projection,
                    pos,
                    orientationPath(spanPath, spanPathIndex, segmentPath, j),
                    pathIndex,
                    category);
            }
        }
    }

    private static void placeOrientedBlock(
            MaterialMix material,
            PowerLineFootprint footprint,
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            BlockPos pos,
            List<BlockPos> orientationPath,
            int pathIndex,
            PlacementCategory category) {
        String blockId = MaterialMixResolver.resolve(material, pos, footprint.getId());
        String placementId = DirectionalBlockSpecs.resolveWirePlacementAlongPath(
            blockId,
            orientationPath,
            pathIndex).toSetBlockArgument();
        PlacementWriter.put(result, projection, pos, placementId, category);
    }

    private static int orientationIndex(
            Map<BlockPos, Integer> spanPathIndex,
            List<BlockPos> segmentPath,
            List<BlockPos> spanPath,
            int segmentIndex) {
        BlockPos center = segmentPath.get(segmentIndex);
        Integer indexed = spanPathIndex.get(center);
        if (indexed != null) {
            return indexed;
        }
        return Math.min(segmentIndex, Math.max(0, spanPath.size() - 1));
    }

    private static List<BlockPos> orientationPath(
            List<BlockPos> spanPath,
            Map<BlockPos, Integer> spanPathIndex,
            List<BlockPos> segmentPath,
            int segmentIndex) {
        if (spanPath != null && !spanPath.isEmpty()) {
            return spanPath;
        }
        return segmentPath;
    }

}
