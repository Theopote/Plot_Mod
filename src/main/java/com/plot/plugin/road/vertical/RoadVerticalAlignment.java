package com.plot.plugin.road.vertical;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路纵断面：有序 PVI 列表。
 * <p>
 * 相邻 PVI 间为坡度切线；中间 PVI 可附对称抛物线竖曲线（长度或 K 值导出）。
 * 空列表表示未定义纵断面（仍使用节点标高 / 坡度求解器）。
 */
public final class RoadVerticalAlignment {

    private final List<PointOfVerticalIntersection> pvis = new ArrayList<>();

    public RoadVerticalAlignment() {
    }

    public RoadVerticalAlignment(List<PointOfVerticalIntersection> pvis) {
        if (pvis != null) {
            for (PointOfVerticalIntersection pvi : pvis) {
                addPvi(pvi);
            }
        }
    }

    public List<PointOfVerticalIntersection> getPvis() {
        return List.copyOf(pvis);
    }

    public void addPvi(PointOfVerticalIntersection pvi) {
        if (pvi != null) {
            pvis.add(pvi);
        }
    }

    public void clearPvis() {
        pvis.clear();
    }

    public boolean isEmpty() {
        return pvis.isEmpty();
    }

    public int pviCount() {
        return pvis.size();
    }

    public double startStation() {
        return pvis.isEmpty() ? 0.0 : pvis.getFirst().getStation();
    }

    public double endStation() {
        return pvis.isEmpty() ? 0.0 : pvis.getLast().getStation();
    }

    /**
     * 存储顺序桩号是否严格递增（无重复、无乱序）。
     */
    public boolean hasStrictlyIncreasingStorageOrder() {
        for (int i = 1; i < pvis.size(); i++) {
            if (pvis.get(i).getStation() <= pvis.get(i - 1).getStation() + 1e-9) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按桩号升序返回 PVI；仅当存储顺序已严格递增时与 {@link #getPvis()} 一致，否则 empty。
     */
    public List<PointOfVerticalIntersection> sortedPvis() {
        if (!hasStrictlyIncreasingStorageOrder()) {
            return List.of();
        }
        return List.copyOf(pvis);
    }

    public boolean isValid() {
        return pvis.size() <= 1 || hasStrictlyIncreasingStorageOrder();
    }

    public RoadVerticalAlignment copy() {
        List<PointOfVerticalIntersection> copied = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : pvis) {
            copied.add(pvi.copy());
        }
        return new RoadVerticalAlignment(copied);
    }
}
