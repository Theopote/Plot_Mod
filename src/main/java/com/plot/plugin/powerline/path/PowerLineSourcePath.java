package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;

import java.util.List;

/**
 * 电力线路参考路径：沿画布几何按世界里程（blocks）求值，不预离散为折线顶点。
 */
public interface PowerLineSourcePath {

    boolean isClosed();

    double worldLength(ICoordinateService coordinates);

    Vec2d pointAtStation(double worldStationBlocks, ICoordinateService coordinates);

    Vec2d tangentAtStation(double worldStationBlocks, ICoordinateService coordinates);

    /**
     * 必须立杆的世界里程：开放路径含起终点；折线含转角顶点；曲线含锚点衔接处锐角。
     */
    List<Double> mandatoryStations(double cornerAngleThresholdDeg, ICoordinateService coordinates);

    /**
     * 将画布点投影到路径上，返回最近点的世界里程（blocks）。
     */
    double stationAtPoint(Vec2d canvasPoint, ICoordinateService coordinates);
}
