package com.plot.plugin.road.ui;

import com.plot.plugin.road.lamp.RoadLampCompiler;
import com.plot.plugin.road.tunnel.TunnelStyle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelSegmentPreviewSceneTest {

    @Test
    void accentRingsAppearOnlyOnSpacingStations() {
        TunnelStyle style = new TunnelStyle();
        style.setAccentRings(true);
        style.setAccentMaterial("minecraft:gold_block");
        style.setAccentSpacing(4);
        style.setLightingMode(com.plot.plugin.road.tunnel.TunnelLightingMode.NONE);

        List<RoadLampCompiler.Voxel> scene = TunnelSegmentPreviewScene.build(style, 5);
        assertFalse(scene.isEmpty());

        Set<Integer> accentZs = scene.stream()
            .filter(v -> "minecraft:gold_block".equals(v.block()))
            .map(v -> v.position().getZ())
            .collect(Collectors.toSet());
        assertFalse(accentZs.isEmpty());
        for (int z : accentZs) {
            assertEquals(0, Math.floorMod(z, 4), "accent only on spacing stations, z=" + z);
        }

        Set<Integer> liningZs = scene.stream()
            .filter(v -> style.getLiningMaterial().equals(v.block()))
            .map(v -> v.position().getZ())
            .collect(Collectors.toSet());
        assertTrue(liningZs.stream().anyMatch(z -> Math.floorMod(z, 4) != 0),
            "normal stations keep lining material");
    }

    @Test
    void withoutEffectiveRingsNoAccentMaterial() {
        TunnelStyle style = new TunnelStyle();
        style.setAccentRings(true);
        style.setAccentMaterial("");
        style.setAccentSpacing(4);

        List<RoadLampCompiler.Voxel> scene = TunnelSegmentPreviewScene.build(style, 5);
        assertTrue(scene.stream().noneMatch(v ->
            v.block() != null && v.block().contains("gold")));
        assertTrue(TunnelSegmentPreviewScene.hitsSpacing(0, 4));
        assertFalse(TunnelSegmentPreviewScene.hitsSpacing(1, 4));
    }

    @Test
    void cacheKeyAllowsNullLightAnchors() {
        TunnelStyle style = new TunnelStyle();
        Object key = TunnelSegmentPreviewScene.cacheKey(style, 5);
        assertTrue(key != null);
        // Rebuild must also succeed with default null anchors (no custom lights).
        assertFalse(TunnelSegmentPreviewScene.build(style, 5).isEmpty());
    }
}
