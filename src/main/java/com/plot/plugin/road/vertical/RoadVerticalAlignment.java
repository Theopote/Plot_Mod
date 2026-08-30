package com.plot.plugin.road.vertical;

import java.util.ArrayList;
import java.util.Comparator;
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
     * 按桩号升序返回 PVI；若桩号非严格递增则返回 empty。
     */
    public List<PointOfVerticalIntersection> sortedPvis() {
        if (pvis.size() < 2) {
            return List.copyOf(pvis);
        }
        List<PointOfVerticalIntersection> sorted = new ArrayList<>(pvis);
        sorted.sort(Comparator.comparingDouble(PointOfVerticalIntersection::getStation));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).getStation() <= sorted.get(i - 1).getStation()) {
                return List.of();
            }
        }
        return List.copyOf(sorted);
    }

    public boolean isValid() {
        if (pvis.size() <= 1) {
            return true;
        }
        return sortedPvis().size() == pvis.size();
    }

    public RoadVerticalAlignment copy() {
        List<PointOfVerticalIntersection> copied = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : pvis) {
            copied.add(pvi.copy());
        }
        return new RoadVerticalAlignment(copied);
    }
}
