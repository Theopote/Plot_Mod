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
        Vec2d center = fillet.center();
        Vec2d start = fillet.tangentStart();
        Vec2d end = fillet.tangentEnd();
        double startAngle = Math.atan2(start.y - center.y, start.x - center.x);
        // 切点圆心角与草地楔形互补；只用短弧，勿用草地角把扫掠翻成近整圆
        double sweep = FilletArcSweep.resolveShort(center, start, end);
        int segs = Math.max(3, segments);
        List<Vec2d> out = new ArrayList<>(segs + 1);
        out.add(start.copy());
        for (int i = 1; i < segs; i++) {
            double t = (double) i / segs;
            out.add(FilletArcSweep.pointOnArc(center, fillet.radius(), startAngle + sweep * t));
        }
        out.add(end.copy());
        return out;
    }
}
