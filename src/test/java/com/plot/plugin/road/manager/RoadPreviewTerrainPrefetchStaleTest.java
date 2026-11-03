package com.plot.plugin.road.manager;

import com.plot.core.command.BlockRecord;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RoadPreviewTerrainPrefetchStaleTest {

    private RoadPreviewManager previewManager;

    @BeforeEach
    void setUp() {
        PluginContext host = PluginContext.from(ApplicationContext.getInstance());
        previewManager = new RoadPreviewManager(new RoadProjectStatus(), host);
    }

    @Test
    void successfulPreviewClearsPendingTerrainRefreshBeforeStatusSideEffects() throws Exception {
        setField(previewManager, "pendingFullPreviewTerrainRefresh", true);
        setField(previewManager, "previewNeedsRecalc", true);

        RoadGenerationResult aggregate = new RoadGenerationResult(10.0);
        BlockPos pos = new BlockPos(0, 64, 0);
        aggregate.placementRecords.put(
            pos, new BlockRecord(pos, "minecraft:grass_block", "minecraft:stone"));
        RoadNetworkGenerator.PreviewResult previewResult = new RoadNetworkGenerator.PreviewResult(
            new RoadNetwork(),
            aggregate,
            Map.of(),
            Map.of(),
            new RoadNetworkGenerator.NetworkGenerationResult(),
            Map.of());

        Method apply = RoadPreviewManager.class.getDeclaredMethod(
            "applyPreviewResult", RoadNetwork.class, RoadNetworkGenerator.PreviewResult.class, boolean.class);
        apply.setAccessible(true);
        try {
            apply.invoke(previewManager, new RoadNetwork(), previewResult, false);
        } catch (java.lang.reflect.InvocationTargetException ignored) {
            // applyPreviewReadyStatus may require client coordinate services in unit tests.
        }

        assertFalse(previewManager.needsPreviewRecalc(),
            "successful preview must clear stale before optional status/visibility side effects");
        assertFalse((boolean) getField(previewManager, "pendingFullPreviewTerrainRefresh"),
            "pending terrain refresh must not remain armed after an explicit successful preview");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
