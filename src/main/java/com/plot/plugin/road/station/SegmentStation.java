package com.plot.plugin.road.station;

/**
 * 边内局部里程：分段 ID + 从该段起点起的距离（米）。
 */
public record SegmentStation(String segmentId, double localDistance) {
}
