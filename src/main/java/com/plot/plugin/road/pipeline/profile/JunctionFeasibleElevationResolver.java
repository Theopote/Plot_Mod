package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 路口统一标高的坡度可行域决议：避免简单算术平均导致第二遍纵断面无法到达 junction Y 而出现断崖。
 */
public final class JunctionFeasibleElevationResolver {

    private static final int MAX_ITERATIONS = 5;

    /** 从某节点沿一条相连边到对端节点时可用的坡度约束。 */
    public record ApproachConstraint(String oppositeNodeId, double lengthBlocks, float maxSlopePercent) {
    }

    private JunctionFeasibleElevationResolver() {
    }

    /**
     * 在初始决议基础上，按各边坡度与长度迭代收敛到可行路口标高。
     */
    public static Map<String, Integer> refine(
            RoadNetwork network,
            Map<String, List<Integer>> naturalSamplesByNode,
            Map<String, List<ApproachConstraint>> constraintsByNode,
            Map<String, Integer> initialHeights) {
        Map<String, Integer> current = new LinkedHashMap<>(initialHeights);
        if (network == null || constraintsByNode.isEmpty()) {
            return current;
        }
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            boolean changed = false;
            for (RoadNode node : network.getNodes().values()) {
                if (node == null || node.getManualElevation() != null) {
                    continue;
                }
                String nodeId = node.getId();
                List<ApproachConstraint> constraints = constraintsByNode.getOrDefault(nodeId, List.of());
                if (constraints.size() < 2) {
                    continue;
                }
                int preferred = preferredHeight(naturalSamplesByNode.get(nodeId), current.get(nodeId));
                int resolved = resolveFeasibleHeight(preferred, constraints, current);
                Integer previous = current.get(nodeId);
                if (previous == null || previous != resolved) {
                    current.put(nodeId, resolved);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return current;
    }

    static int resolveFeasibleHeight(
            int preferred,
            List<ApproachConstraint> constraints,
            Map<String, Integer> nodeHeights) {
        double feasibilityMin = Double.NEGATIVE_INFINITY;
        double feasibilityMax = Double.POSITIVE_INFINITY;
        for (ApproachConstraint constraint : constraints) {
            Integer oppositeHeight = nodeHeights.get(constraint.oppositeNodeId());
            if (oppositeHeight == null) {
                continue;
            }
            double maxRise = constraint.lengthBlocks() * constraint.maxSlopePercent() / 100.0;
            feasibilityMin = Math.max(feasibilityMin, oppositeHeight - maxRise);
            feasibilityMax = Math.min(feasibilityMax, oppositeHeight + maxRise);
        }
        if (feasibilityMin <= feasibilityMax) {
            double clamped = Math.max(feasibilityMin, Math.min(feasibilityMax, preferred));
            return (int) Math.round(clamped);
        }
        // 可行域不相交：取冲突边界中点，使各边以最小超限坡度趋近路口。
        return (int) Math.round((feasibilityMin + feasibilityMax) * 0.5);
    }

    private static int preferredHeight(List<Integer> samples, Integer current) {
        if (samples != null && !samples.isEmpty()) {
            return RoadSlopeUtils.resolveJunctionHeight(samples).height();
        }
        return current != null ? current : 64;
    }
}
