package com.plot.plugin.earthwork.geometry;
import com.plot.plugin.earthwork.model.Breakline;
import com.plot.plugin.earthwork.model.RetainingEdge;

import java.util.ArrayList;
import java.util.List;

/**
 * 将挡土界转为合成用虚拟折线（禁止跨界混合）。
 */
public final class RetainingEdgeBreaklineAdapter {
    private RetainingEdgeBreaklineAdapter() {
    }

    public static List<Breakline> toNoBlendBreaklines(List<RetainingEdge> retainingEdges) {
        if (retainingEdges == null || retainingEdges.isEmpty()) {
            return List.of();
        }
        List<Breakline> breaklines = new ArrayList<>();
        for (RetainingEdge retainingEdge : retainingEdges) {
            if (retainingEdge == null || retainingEdge.getPolyline().size() < 2) {
                continue;
            }
            Breakline breakline = new Breakline("retaining:" + retainingEdge.getId());
            breakline.setName(retainingEdge.getName());
            breakline.setPoints(retainingEdge.getPolyline());
            breakline.setRole(Breakline.ROLE_NO_BLENDING);
            breaklines.add(breakline);
        }
        return breaklines;
    }
}
