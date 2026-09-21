package com.plot.plugin.powerline.design.structure;

import java.util.List;

/**
 * 四腿格构塔纵深（halfDepth）收分：侧面剪影应下宽上窄，与正面收分一致或略陡。
 * <p>
 * 设计稿里 width/depth 比例往往已同步，但纵深绝对尺寸更小，体素取整后侧视更容易显得“直筒”；
 * 生成前按当前截面半宽对半深做幂次收分。仅在原始轮廓处于收分阶段时强制单调变窄；
 * 酒杯塔杯口、门架下部等主动外扩段保留设计稿外扩。
 */
public final class TowerStationFootprintTaper {
    /** 纵深相对正面收分的额外指数；&gt;1 使上半段半深收得更陡。 */
    public static final double DEPTH_SIDE_TAPER_EXPONENT = 1.15;

    private static final double MIN_HALF_DEPTH = 0.45;

    private TowerStationFootprintTaper() {
    }

    public static void applySideDepthTaper(TowerStructureDesign structure) {
        if (structure == null) {
            return;
        }
        List<TowerStation> stations = structure.sortedStations();
        if (stations.size() < 2) {
            return;
        }
        TowerStation base = stations.getFirst();
        double baseHalfWidth = base.getHalfWidth();
        double baseHalfDepth = base.getHalfDepth();
        if (baseHalfWidth < 1e-6 || baseHalfDepth < 1e-6) {
            return;
        }

        double[] originalWidths = new double[stations.size()];
        double[] originalDepths = new double[stations.size()];
        for (int i = 0; i < stations.size(); i++) {
            originalWidths[i] = stations.get(i).getHalfWidth();
            originalDepths[i] = stations.get(i).getHalfDepth();
        }

        double previousDepth = baseHalfDepth;
        for (int i = 1; i < stations.size(); i++) {
            TowerStation station = stations.get(i);
            double widthFactor = Math.max(station.getHalfWidth() / baseHalfWidth, 0.01);
            double coupledDepth = baseHalfDepth * Math.pow(widthFactor, DEPTH_SIDE_TAPER_EXPONENT);
            double nextDepth = Math.min(originalDepths[i], coupledDepth);

            boolean intentionalExpansion =
                originalWidths[i] > originalWidths[i - 1]
                || originalDepths[i] > originalDepths[i - 1];
            if (!intentionalExpansion) {
                nextDepth = Math.min(nextDepth, previousDepth);
            }

            nextDepth = Math.max(nextDepth, MIN_HALF_DEPTH);
            station.setHalfDepth(nextDepth);
            previousDepth = nextDepth;
        }
    }
}
