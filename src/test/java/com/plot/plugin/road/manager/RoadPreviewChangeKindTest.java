package com.plot.plugin.road.manager;

import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.road.solid.RoadGenerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPreviewChangeKindTest {

    private RoadPreviewManager previewManager;

    @BeforeEach
    void setUp() throws Exception {
        PluginContext host = PluginContext.from(ApplicationContext.getInstance());
        previewManager = new RoadPreviewManager(new RoadProjectStatus(), host);
        seedProfileSampling(previewManager);
    }

    @Test
    void verticalProfileChangePreservesProfileSampling() {
        previewManager.handleNetworkChanged(RoadChangeKind.VERTICAL_PROFILE);

        assertTrue(previewManager.hasProfileSampling());
        assertNull(previewManager.getLastGenerationResult());
        assertTrue(previewManager.needsPreviewRecalc());
    }

    @Test
    void flatBaseElevationChangePreservesProfileSampling() {
        previewManager.handleNetworkChanged(RoadChangeKind.VERTICAL_PROFILE);

        assertTrue(previewManager.hasProfileSampling());
        assertNull(previewManager.getLastGenerationResult());
        assertTrue(previewManager.needsPreviewRecalc());
    }

    @Test
    void junctionChangePreservesProfileSampling() {
        previewManager.handleNetworkChanged(RoadChangeKind.JUNCTION);

        assertTrue(previewManager.hasProfileSampling());
        assertNull(previewManager.getLastGenerationResult());
        assertTrue(previewManager.needsPreviewRecalc());
    }

    @Test
    void geometryChangeClearsProfileSampling() {
        previewManager.handleNetworkChanged(RoadChangeKind.GEOMETRY);

        assertFalse(previewManager.hasProfileSampling());
        assertTrue(previewManager.needsPreviewRecalc());
    }

    private static void seedProfileSampling(RoadPreviewManager previewManager) throws Exception {
        RoadGenerationResult edgeResult = new RoadGenerationResult(100.0);
        edgeResult.profileDistances = List.of(0.0, 100.0);
        edgeResult.profileGroundHeights = List.of(64, 65);
        edgeResult.profileBuildHeights = List.of(66, 67);

        setField(previewManager, "lastEdgeResults", Map.of("edge-1", edgeResult));
        setField(previewManager, "lastGenerationResult", new RoadGenerationResult(50.0));
        setField(previewManager, "previewNeedsRecalc", false);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
