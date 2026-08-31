package com.plot.plugin.road.station;

/**
 * 编辑 UI 桩号只读展示上下文。
 */
public record ChainageDisplayContext(
        double totalLength,
        ChainageDisplayMode mode,
        RoadStationFormat format) {

    public ChainageDisplayContext {
        if (format == null) {
            format = RoadStationFormat.KILOMETER_PLUS;
        }
        if (mode == null) {
            mode = ChainageDisplayMode.FROM_START;
        }
    }

    public static ChainageDisplayContext fromStart(double totalLength) {
        return new ChainageDisplayContext(totalLength, ChainageDisplayMode.FROM_START, RoadStationFormat.KILOMETER_PLUS);
    }

    public String format(double chainageMeters) {
        return RoadStationing.format(chainageMeters, totalLength, format, mode);
    }

    public String format(RoadStation station) {
        if (station == null) {
            return "-";
        }
        return format(station.chainageMeters());
    }

    public String formatRange(double startMeters, double endMeters) {
        return format(startMeters) + " – " + format(endMeters);
    }
}
