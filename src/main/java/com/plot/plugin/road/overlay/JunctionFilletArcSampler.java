package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.FilletArcSweep;
import com.plot.plugin.road.JunctionGeometryResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 {@link JunctionGeometryResult.FilletCorner} 采样为画布可描的开折线圆弧。
 */
public final class JunctionFilletArcSampler {
    private static final int DEFAULT_SEGMENTS = 12;

    private JunctionFilletArcSampler() {
    }

    public static List<List<Vec2d>> sampleArcs(JunctionGeometryResult geometry) {
        return sampleArcs(geometry, DEFAULT_SEGMENTS);
    }

    public static List<List<Vec2d>> sampleArcs(JunctionGeometryResult geometry, int segments) {
        if (geometry == null || geometry.fillets().isEmpty()) {
            return List.of();
        }
        int segs = Math.max(3, segments);
        List<List<Vec2d>> arcs = new ArrayList<>(geometry.fillets().size());
        for (JunctionGeometryResult.FilletCorner fillet : geometry.fillets()) {
            List<Vec2d> polyline = sampleCorner(fillet, segs);
            if (polyline.size() >= 2) {
                arcs.add(List.copyOf(polyline));
            }
        }
        return List.copyOf(arcs);
    }

    static List<Vec2d> sampleCorner(JunctionGeometryResult.FilletCorner fillet, int segments) {
        if (fillet == null
                || fillet.center() == null
                || fillet.tangentStart() == null
                || fillet.tangentEnd() == null
                || fillet.radius() <= 1e-9) {
            return List.of();
        }
        // 使用求解阶段已固化的 sweep，预览与 Minecraft 补铺同一几何
        return FilletArcSweep.sample(
            fillet.center(),
            fillet.tangentStart(),
            fillet.tangentEnd(),
            fillet.radius(),
            fillet.sweepRadians(),
            segments).points();
    }
}
