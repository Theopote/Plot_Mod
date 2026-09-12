package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.plugin.powerline.model.TowerRole;

import java.util.List;

/** 根据路径几何自动分配杆塔角色。 */
public final class TowerRoleClassifier {
    private TowerRoleClassifier() {
    }

    /**
     * 偏转角语义：直线 = 0°，90° 转角 = 90°，U 形 ≈ 180°。
     * 计算相邻路径段方向向量之间的夹角（度）。
     */
    public static double computeDeflectionAngle(Vec2d incoming, Vec2d outgoing) {
        if (incoming == null || outgoing == null) {
            return 0.0;
        }
        if (incoming.lengthSquared() < 1e-12 || outgoing.lengthSquared() < 1e-12) {
            return 0.0;
        }
        double dot = incoming.normalize().dot(outgoing.normalize());
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(Math.acos(dot));
    }

    public static void classifySites(List<PowerPoleSite> sites, double angleThresholdDegrees) {
        classifySites(sites, angleThresholdDegrees, false);
    }

    public static void classifySites(
            List<PowerPoleSite> sites,
            double angleThresholdDegrees,
            boolean closedLoop) {
        if (sites == null || sites.isEmpty()) {
            return;
        }
        double threshold = Math.max(0.0, angleThresholdDegrees);
        int size = sites.size();
        if (size == 1) {
            PowerPoleSite site = sites.getFirst();
            if (site.isRoleAutoAssigned()) {
                site.setRole(closedLoop ? TowerRole.SUSPENSION : TowerRole.TERMINAL);
                site.setDeflectionAngle(0.0);
            }
            return;
        }

        for (int i = 0; i < size; i++) {
            PowerPoleSite site = sites.get(i);
            if (!site.isRoleAutoAssigned()) {
                continue;
            }
            if (!closedLoop && (i == 0 || i == size - 1)) {
                site.setRole(TowerRole.TERMINAL);
                site.setDeflectionAngle(0.0);
                continue;
            }

            int previousIndex = closedLoop ? (i - 1 + size) % size : i - 1;
            int nextIndex = closedLoop ? (i + 1) % size : i + 1;
            Vec2d incoming = sites.get(i).getPlanPosition()
                .subtract(sites.get(previousIndex).getPlanPosition());
            Vec2d outgoing = sites.get(nextIndex).getPlanPosition()
                .subtract(sites.get(i).getPlanPosition());
            double deflection = computeDeflectionAngle(incoming, outgoing);
            site.setDeflectionAngle(deflection);
            site.setRole(deflection >= threshold ? TowerRole.ANGLE : TowerRole.SUSPENSION);
        }
    }
}
