package com.plot.plugin.road.ui;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.ChainageDisplayContext;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;

/**
 * 道路长度 UI 展示：数值为 Minecraft 方块数，约定 {@code 1 m = 1 格}。
 * <p>
 * 内部桩号/折线弧长多为画布域；展示前须经世界投影或按总长比例换算，
 * 禁止把 {@link RoadEdge#getLength()} / {@link RoadStationing#canonicalLength}
 * 直接标成米。
 */
public final class RoadLengthDisplay {
    private static final double EPSILON = 1e-9;

    private RoadLengthDisplay() {
    }

    /** 道路中心线世界长度（blocks）。 */
    public static double roadBlocks(RoadNetwork network, Road road, ICoordinateService coordinates) {
        return RoadEdgeListHelper.computeRoadWorldLength(network, road, coordinates);
    }

    /** 单段中心线世界长度（blocks）。 */
    public static double edgeBlocks(RoadEdge edge, ICoordinateService coordinates) {
        return RoadEdgeListHelper.computeEdgeWorldLength(edge, coordinates);
    }

    /**
     * 画布域 → 方块：按该路「实例折线画布总长 / 世界总长」比例缩放。
     * 用于 HA 设计长、桩号、偏差等无独立折线可投的量。
     */
    public static double canvasToBlocks(
            double canvasLength,
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates) {
        double scale = canvasToWorldScale(network, road, coordinates);
        return canvasLength * scale;
    }

    public static double canvasToWorldScale(
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates) {
        if (network == null || road == null || coordinates == null) {
            return 1.0;
        }
        double canvasTotal = RoadPlanGeometry.instanceLength(network, road);
        if (canvasTotal <= EPSILON) {
            // 无实例几何时退回 canonical（例如仅有 HA）
            canvasTotal = RoadStationing.canonicalLength(network, road);
        }
        if (canvasTotal <= EPSILON) {
            return 1.0;
        }
        double worldTotal = roadBlocks(network, road, coordinates);
        if (worldTotal <= EPSILON) {
            // 有 HA、尚无折线实例时：无法投影，保持 1:1
            return 1.0;
        }
        return worldTotal / canvasTotal;
    }

    public static String formatRoadMeters(RoadNetwork network, Road road, ICoordinateService coordinates) {
        return RoadUiFormat.formatMeters(roadBlocks(network, road, coordinates));
    }

    public static String formatEdgeMeters(RoadEdge edge, ICoordinateService coordinates) {
        return RoadUiFormat.formatMeters(edgeBlocks(edge, coordinates));
    }

    public static String formatCanvasMeters(
            double canvasLength,
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates) {
        return RoadUiFormat.formatMeters(canvasToBlocks(canvasLength, network, road, coordinates));
    }

    public static String formatCanvasDistance(
            double canvasLength,
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates) {
        return RoadUiFormat.formatDistance(canvasToBlocks(canvasLength, network, road, coordinates));
    }

    /** 将画布域桩号格式化为世界（blocks / m）展示。 */
    public static String formatChainage(
            ChainageDisplayContext display,
            double canvasStation,
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates) {
        double scale = canvasToWorldScale(network, road, coordinates);
        double worldStation = canvasStation * scale;
        if (display == null) {
            return RoadStationing.format(worldStation, RoadStationFormat.DISTANCE_METERS);
        }
        double worldTotal = display.totalLength() * scale;
        return new ChainageDisplayContext(worldTotal, display.mode(), display.format()).format(worldStation);
    }

    public static ChainageDisplayContext worldChainageContext(
            ChainageDisplayContext canvasDisplay,
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates) {
        if (canvasDisplay == null) {
            return ChainageDisplayContext.fromStart(roadBlocks(network, road, coordinates));
        }
        double scale = canvasToWorldScale(network, road, coordinates);
        return new ChainageDisplayContext(
            canvasDisplay.totalLength() * scale,
            canvasDisplay.mode(),
            canvasDisplay.format());
    }
}
