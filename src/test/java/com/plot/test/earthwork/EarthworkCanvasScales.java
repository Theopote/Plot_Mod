package com.plot.test.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.geometry.EarthworkCanvasScale;
import com.plot.test.world.IdentityCoordinateService;

import java.util.List;

/** 测试用 1:1 投影尺度（经 {@link EarthworkCanvasScale#capture} 冻结，非 silent fallback）。 */
public final class EarthworkCanvasScales {
    private static final List<Vec2d> DEFAULT_REF = List.of(
        new Vec2d(0, 0), new Vec2d(1, 0), new Vec2d(0, 1));

    private EarthworkCanvasScales() {
    }

    public static EarthworkCanvasScale capture(List<Vec2d> referencePoints) {
        List<Vec2d> ref = referencePoints != null && referencePoints.size() >= 3
            ? referencePoints
            : DEFAULT_REF;
        return EarthworkCanvasScale.capture(IdentityCoordinateService.INSTANCE, ref);
    }
}
