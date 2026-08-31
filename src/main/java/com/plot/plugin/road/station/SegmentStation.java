package com.plot.plugin.road.station;

/**
 * 边内局部里程：分段 ID + 从该段几何起点起的距离（米）。
 * <p>
 * 与 {@link RoadStationing#resolve} 输出一致；当分段几何方向与道路链相反时，
 * 数值为几何局部距离而非链局部距离。
 */
public record SegmentStation(String segmentId, double localDistance) {
}
