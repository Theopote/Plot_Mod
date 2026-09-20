package com.plot.plugin.powerline.path;

/**
 * 闭合线路里程算术：档距、跨中、归一化与最近距离。
 * <p>
 * 开放线路退化为线性 {@code |a-b|} / 算术平均；闭合线路走最短弧。
 */
public final class ClosedPathStationMath {
    private ClosedPathStationMath() {
    }

    /** 沿路径的最短世界里程距离（blocks）。 */
    public static double distance(
            double fromStation,
            double toStation,
            double perimeterBlocks,
            boolean closedLoop) {
        double direct = Math.abs(toStation - fromStation);
        if (!closedLoop || perimeterBlocks <= 1e-12) {
            return direct;
        }
        return Math.min(direct, Math.max(0.0, perimeterBlocks - direct));
    }

    /**
     * 沿闭合环路前进方向的世界里程（blocks）：{@code from → to} 不折返。
     * 用于相邻杆塔档距；缝合点 {@code 185 → 0} 在周长 200 时为 15。
     */
    public static double forwardDistance(
            double fromStation,
            double toStation,
            double perimeterBlocks) {
        if (perimeterBlocks <= 1e-12) {
            return Math.abs(toStation - fromStation);
        }
        double distance = toStation - fromStation;
        if (distance < 0.0) {
            distance += perimeterBlocks;
        }
        return distance;
    }

    /** 两里程之间的跨中（闭合时沿最短弧）。 */
    public static double midpoint(
            double startStation,
            double endStation,
            double perimeterBlocks,
            boolean closedLoop) {
        if (!closedLoop || perimeterBlocks <= 1e-12) {
            return (startStation + endStation) * 0.5;
        }
        double forward = endStation - startStation;
        if (forward < 0.0) {
            forward += perimeterBlocks;
        }
        double backward = perimeterBlocks - forward;
        if (backward < forward) {
            return normalize(startStation - backward * 0.5, perimeterBlocks);
        }
        return normalize(startStation + forward * 0.5, perimeterBlocks);
    }

    /** 将里程归一化到 {@code [0, perimeter)}。 */
    public static double normalize(double stationBlocks, double perimeterBlocks) {
        if (perimeterBlocks <= 1e-12) {
            return Math.max(0.0, stationBlocks);
        }
        double normalized = stationBlocks % perimeterBlocks;
        if (normalized < 0.0) {
            normalized += perimeterBlocks;
        }
        if (normalized >= perimeterBlocks - 1e-9) {
            normalized = 0.0;
        }
        return normalized;
    }

    /** 某里程到目标里程的最短环向距离。 */
    public static double nearestDistance(
            double stationBlocks,
            double targetStationBlocks,
            double perimeterBlocks,
            boolean closedLoop) {
        return distance(stationBlocks, targetStationBlocks, perimeterBlocks, closedLoop);
    }
}
