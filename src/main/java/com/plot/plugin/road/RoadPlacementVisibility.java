package com.plot.plugin.road;

import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.core.command.BlockRecord;
import com.plot.utils.PlotI18n;
import net.minecraft.util.math.BlockPos;

/**
 * 判断道路落地范围是否落在当前正交摄像机对应的 Minecraft 视野内。
 */
public final class RoadPlacementVisibility {

    public enum Status {
        FULLY_VISIBLE,
        PARTIALLY_OUTSIDE,
        FULLY_OUTSIDE,
        UNKNOWN
    }

    public record Analysis(
        Status status,
        CoordinateTransformer.WorldViewBounds viewBounds,
        int blockCount,
        int minX,
        int maxX,
        int minZ,
        int maxZ
    ) {
        public boolean requiresWarning() {
            return status == Status.PARTIALLY_OUTSIDE
                || status == Status.FULLY_OUTSIDE
                || status == Status.UNKNOWN;
        }
    }

    private RoadPlacementVisibility() {
    }

    public static Analysis analyze(RoadGenerationResult result, CoordinateTransformer transformer) {
        CoordinateTransformer.WorldViewBounds viewBounds = transformer != null
            ? transformer.getMinecraftWorldViewBounds()
            : null;
        return analyze(result, viewBounds);
    }

    public static Analysis analyze(
            RoadGenerationResult result,
            CoordinateTransformer.WorldViewBounds viewBounds) {
        if (result == null || result.placementRecords.isEmpty()) {
            return new Analysis(Status.FULLY_VISIBLE, viewBounds, 0, 0, 0, 0, 0);
        }

        if (viewBounds == null) {
            return new Analysis(
                Status.UNKNOWN,
                null,
                result.placementRecords.size(),
                0,
                0,
                0,
                0
            );
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockRecord record : result.placementRecords.values()) {
            BlockPos pos = record.pos;
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        Status status;
        if (viewBounds.containsBox(minX, maxX, minZ, maxZ)) {
            status = Status.FULLY_VISIBLE;
        } else if (viewBounds.intersectsBox(minX, maxX, minZ, maxZ)) {
            status = Status.PARTIALLY_OUTSIDE;
        } else {
            status = Status.FULLY_OUTSIDE;
        }

        return new Analysis(
            status,
            viewBounds,
            result.placementRecords.size(),
            minX,
            maxX,
            minZ,
            maxZ
        );
    }

    public static String formatWarningMessage(Analysis analysis) {
        if (analysis == null || !analysis.requiresWarning()) {
            return "";
        }

        return switch (analysis.status()) {
            case PARTIALLY_OUTSIDE -> PlotI18n.tr(
                "plugin.road.visibility_partial",
                analysis.minX(),
                analysis.maxX(),
                analysis.minZ(),
                analysis.maxZ()
            );
            case FULLY_OUTSIDE -> PlotI18n.tr(
                "plugin.road.visibility_outside",
                analysis.minX(),
                analysis.maxX(),
                analysis.minZ(),
                analysis.maxZ()
            );
            case UNKNOWN -> PlotI18n.tr("plugin.road.visibility_unknown");
            default -> "";
        };
    }
}
