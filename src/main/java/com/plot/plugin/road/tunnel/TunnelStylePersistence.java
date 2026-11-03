package com.plot.plugin.road.tunnel;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON-friendly tunnel style snapshot for {@link com.plot.plugin.road.model.RoadNetwork} persistence.
 */
public final class TunnelStylePersistence {
    public String shape;
    public Integer clearHeight;
    public Integer sideClearance;
    public Integer liningThickness;
    public String liningMaterial;
    public String lightingMode;
    public String lightMaterial;
    public Integer lightSpacing;
    public String lightMount;
    /** Present (incl. empty) when a custom light layout is stored. */
    public List<LightAnchorData> lightAnchors;
    public Boolean accentRings;
    public String accentMaterial;
    public Integer accentSpacing;

    public static final class LightAnchorData {
        public int lateral;
        public int yAboveRoad;
        public String type;
    }

    public static TunnelStylePersistence from(TunnelStyle style) {
        if (style == null || style.inheritsAll()) {
            return null;
        }
        TunnelStylePersistence data = new TunnelStylePersistence();
        if (style.getStoredShape() != null) {
            data.shape = style.getStoredShape().name();
        }
        data.clearHeight = style.getStoredClearHeight();
        data.sideClearance = style.getStoredSideClearance();
        data.liningThickness = style.getStoredLiningThickness();
        data.liningMaterial = style.getStoredLiningMaterial();
        if (style.getStoredLightingMode() != null) {
            data.lightingMode = style.getStoredLightingMode().name();
        }
        data.lightMaterial = style.getStoredLightMaterial();
        data.lightSpacing = style.getStoredLightSpacing();
        if (style.getStoredLightMount() != null) {
            data.lightMount = style.getStoredLightMount().name();
        }
        if (style.getStoredLightAnchors() != null) {
            data.lightAnchors = new ArrayList<>();
            for (TunnelLightAnchor anchor : style.getStoredLightAnchors()) {
                LightAnchorData row = new LightAnchorData();
                row.lateral = anchor.lateral();
                row.yAboveRoad = anchor.yAboveRoad();
                row.type = anchor.type().name();
                data.lightAnchors.add(row);
            }
        }
        data.accentRings = style.getStoredAccentRings();
        data.accentMaterial = style.getStoredAccentMaterial();
        data.accentSpacing = style.getStoredAccentSpacing();
        return data;
    }

    public static TunnelStyle fromData(TunnelStylePersistence data) {
        if (data == null) {
            return null;
        }
        TunnelStyle style = new TunnelStyle();
        style.setShape(TunnelShape.fromStored(data.shape));
        style.setClearHeight(data.clearHeight);
        style.setSideClearance(data.sideClearance);
        style.setLiningThickness(data.liningThickness);
        style.setLiningMaterial(data.liningMaterial);
        style.setLightingMode(TunnelLightingMode.fromStored(data.lightingMode));
        style.setLightMaterial(data.lightMaterial);
        style.setLightSpacing(data.lightSpacing);
        style.setLightMount(TunnelLightMount.fromStored(data.lightMount));
        if (data.lightAnchors != null) {
            List<TunnelLightAnchor> anchors = new ArrayList<>(data.lightAnchors.size());
            for (LightAnchorData row : data.lightAnchors) {
                if (row == null) {
                    continue;
                }
                TunnelLightPlacementResolver.LightType type = TunnelLightPlacementResolver.LightType.WALL;
                if (row.type != null && !row.type.isBlank()) {
                    try {
                        type = TunnelLightPlacementResolver.LightType.valueOf(row.type.trim().toUpperCase());
                    } catch (IllegalArgumentException ignored) {
                        type = TunnelLightPlacementResolver.LightType.WALL;
                    }
                }
                anchors.add(new TunnelLightAnchor(row.lateral, row.yAboveRoad, type));
            }
            style.setLightAnchors(anchors);
        }
        style.setAccentRings(data.accentRings);
        style.setAccentMaterial(data.accentMaterial);
        style.setAccentSpacing(data.accentSpacing);
        return style.inheritsAll() ? null : style;
    }
}
