package com.plot.plugin.road.manager;

import com.plot.core.command.BlockRecord;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPreviewWorldKeyGateTest {

    @Test
    void emptyWorldKeyFailsClosedForBuildPreview() throws Exception {
        AtomicReference<String> worldId = new AtomicReference<>("");
        RoadPreviewManager manager = new RoadPreviewManager(
            new RoadProjectStatus(), PluginContext.from(ApplicationContext.getInstance()));
        manager.setWorldKeyLookup(worldId::get);
        seedPreview(manager, "");

        assertFalse(manager.isBuildPreviewCurrent(), "empty world key must not authorize build");

        worldId.set("save-a|overworld");
        setField(manager, "previewWorldKey", "save-a|overworld");
        assertTrue(manager.isBuildPreviewCurrent());

        worldId.set("save-b|overworld");
        assertFalse(manager.isBuildPreviewCurrent(), "different save identity must stale preview");
    }

    private static void seedPreview(RoadPreviewManager manager, String worldKey) throws Exception {
        RoadGenerationResult aggregate = new RoadGenerationResult(10);
        BlockPos pos = new BlockPos(0, 64, 0);
        aggregate.placementRecords.put(pos, new BlockRecord(pos, "minecraft:air", "minecraft:stone"));
        setField(manager, "lastGenerationResult", aggregate);
        setField(manager, "previewNeedsRecalc", false);
        setField(manager, "previewWorldKey", worldKey);
        setField(manager, "previewNetwork", new RoadNetwork());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
