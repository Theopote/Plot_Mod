package com.plot.plugin.road.lamp;

import com.plot.core.block.BlockSpec;
import com.plot.core.block.BlockStateValidator;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RoadLampCompilerTest {
    @Test
    void everyPresetCompilesToDistinctConnectedValidBlocks() {
        Set<Set<BlockPos>> shapes = new HashSet<>();
        for (var preset : RoadLampPresets.ALL) {
            var voxels = RoadLampCompiler.compile(preset.style());
            Set<BlockPos> positions = voxels.stream().map(RoadLampCompiler.Voxel::position).collect(Collectors.toSet());
            assertEquals(voxels.size(), positions.size(), preset.id());
            assertTrue(shapes.add(positions), preset.id());
            Set<BlockPos> reached = new HashSet<>();
            var queue = new java.util.ArrayDeque<BlockPos>();
            queue.add(BlockPos.ORIGIN);
            while (!queue.isEmpty()) {
                var pos = queue.remove();
                if (!reached.add(pos)) continue;
                for (var direction : net.minecraft.util.math.Direction.values()) {
                    if (positions.contains(pos.offset(direction))) queue.add(pos.offset(direction));
                }
            }
            assertEquals(positions, reached, preset.id());
            for (var voxel : voxels) {
                assertTrue(BlockStateValidator.isValid(BlockSpec.parse(voxel.block())), voxel.block());
            }
        }
        assertEquals(8, shapes.size());
    }

    @Test
    void rotationKeepsElevationAndTurnsCantileverInward() {
        var local = RoadLampCompiler.compile(RoadLampPresets.ALL.get(1).style());
        var world = RoadLampCompiler.position(local, new BlockPos(10, 64, 20), 0, -1);
        for (int i = 0; i < local.size(); i++) {
            var p = local.get(i).position();
            assertEquals(new BlockPos(10 + p.getZ(), 64 + p.getY(), 20 - p.getX()), world.get(i).position());
            assertEquals(local.get(i).block(), world.get(i).block());
        }
    }

    @Test
    void customLibraryRoundTripsMultipleLevelsAndPreservesNamedPresets(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        var style = new RoadLampStyle("两层路灯", 14, RoadLampStyle.Material.WOOD, true, java.util.List.of(
            new RoadLampStyle.Level(5, RoadLampStyle.Layout.TWIN, RoadLampStyle.Mount.TOP, 2, 1, RoadLampStyle.Light.SEA),
            new RoadLampStyle.Level(13, RoadLampStyle.Layout.CROSS, RoadLampStyle.Mount.CHAIN, 3, 2, RoadLampStyle.Light.LANTERN)));
        var file = dir.resolve("lamps.json");
        var library = new RoadLampLibrary(file);
        assertTrue(library.load().isEmpty());
        var entry = library.save(null, style).getFirst();
        assertEquals(style, entry.style());
        assertThrows(IllegalArgumentException.class, () -> library.save(null, style));
        assertEquals(java.util.List.of(entry), new RoadLampLibrary(file).load());
        var renamed = new RoadLampStyle("新名称", style.height(), style.material(), style.plinth(), style.levels());
        assertEquals(new RoadLampLibrary.Entry(entry.id(), renamed), library.save(entry.id(), renamed).getFirst());
        assertTrue(library.delete(entry.id()).isEmpty());
        assertTrue(new RoadLampLibrary(file).load().isEmpty());
    }
}
