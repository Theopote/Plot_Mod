package com.plot.test.world;

import com.plot.api.world.ICoordinateService;
import com.plot.api.world.SnapshotCoordinateService;

/** 测试用坐标服务工厂。 */
public final class CoordinateTestFixtures {
    private CoordinateTestFixtures() {
    }

    public static ICoordinateService uniformScale(double blocksPerCanvasUnit) {
        return SnapshotCoordinateService.uniformScale(blocksPerCanvasUnit);
    }
}
