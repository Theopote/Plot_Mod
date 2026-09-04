package com.plot.plugin.building.golden;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.OpeningKind;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Semantic Acceptance：手写正确性断言（不可由 Snapshot 生成 expected）。
 */
public final class SemanticAcceptanceAssertions {
    private SemanticAcceptanceAssertions() {
    }

    /**
     * 对任意有效 Golden Case 的通用语义门禁。
     */
    public static void assertUniversal(String caseId, GoldenBuildingHarness.Run run) {
        GoldenBuildingMetrics metrics = run.metrics();
        BuildingGenerationContext context = run.context();

        assertTrue(context.isValid(), caseId + " context must be valid");
        assertTrue(metrics.wallBlocks() > 0, caseId + " wallBlockCount > 0");
        assertTrue(metrics.totalBlocks() > 0, caseId + " totalBlocks > 0");
        assertTrue(allFloorsHavePlate(context.getDefinition()),
            caseId + " allFloorsHavePlate");
        assertFalse(hasDisconnectedRoof(run),
            caseId + " roof columns must be 4-connected when present");
        assertDoorsTouchWall(caseId, run);
    }

    public static boolean allFloorsHavePlate(BuildingDefinition definition) {
        if (definition == null || definition.massing() == null) {
            return false;
        }
        int floors = definition.massing().floors();
        for (int floor = 0; floor < floors; floor++) {
            if (definition.massing().plateForFloor(floor) == null) {
                return false;
            }
            if (definition.massing().plateForFloor(floor).outerPoints().size() < 3) {
                return false;
            }
        }
        return true;
    }

    /**
     * 屋顶柱（有 roof 方块的 XZ）若存在，应形成单一 4-连通分量。
     */
    public static boolean hasDisconnectedRoof(GoldenBuildingHarness.Run run) {
        String roofId = normalize(run.context().getRoofBlockId());
        Set<Long> columns = new HashSet<>();
        for (Map.Entry<BlockPos, BlockRecord> entry : run.result().placementRecords.entrySet()) {
            if (!normalize(entry.getValue().newBlockId).equals(roofId)) {
                continue;
            }
            if (isAir(entry.getValue().newBlockId)) {
                continue;
            }
            BlockPos pos = entry.getKey();
            columns.add(pack(pos.getX(), pos.getZ()));
        }
        if (columns.size() <= 1) {
            return false;
        }
        long seed = columns.iterator().next();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(seed);
        while (!queue.isEmpty()) {
            long key = queue.removeFirst();
            int x = (int) (key >> 32);
            int z = (int) key;
            for (long n : new long[] {
                pack(x + 1, z), pack(x - 1, z), pack(x, z + 1), pack(x, z - 1)
            }) {
                if (columns.contains(n) && visited.add(n)) {
                    queue.add(n);
                }
            }
        }
        return visited.size() != columns.size();
    }

    public static void assertDoorsTouchWall(String caseId, GoldenBuildingHarness.Run run) {
        boolean hasDoor = run.context().getDefinition().facade().openings().stream()
            .anyMatch(o -> o.kind() == OpeningKind.DOOR);
        if (!hasDoor) {
            return;
        }
        assertTrue(run.metrics().openingBlocks() > 0,
            caseId + " door openings should carve air");

        String wallId = normalize(
            run.context().getDefinition().envelope().wallMaterial().getPrimaryMaterial());
        Set<BlockPos> air = new HashSet<>();
        Set<BlockPos> walls = new HashSet<>();
        for (Map.Entry<BlockPos, BlockRecord> entry : run.result().placementRecords.entrySet()) {
            String id = normalize(entry.getValue().newBlockId);
            if (isAir(id)) {
                air.add(entry.getKey());
            } else if (id.equals(wallId)) {
                walls.add(entry.getKey());
            }
        }
        assertFalse(air.isEmpty(), caseId + " expected opening air cells");
        int touching = 0;
        for (BlockPos pos : air) {
            if (walls.contains(pos.east())
                || walls.contains(pos.west())
                || walls.contains(pos.north())
                || walls.contains(pos.south())
                || walls.contains(pos.up())
                || walls.contains(pos.down())) {
                touching++;
            }
        }
        assertTrue(touching > 0,
            caseId + " doorTouchesWall: at least one opening air must neighbor a wall block");
        // 多数开洞空气应贴墙（允许少量角点/过深镂空）
        assertTrue(touching / (double) air.size() >= 0.5,
            caseId + " doorTouchesWall: too many floating air cells ("
                + touching + "/" + air.size() + ")");
    }

    /**
     * B07 标杆语义：inner offset 失败后仍须有实心墙，且楼板/屋顶按降级策略。
     * <p>
     * Snapshot 只能证明「和上次一样」；本方法证明「仍然正确」。
     * 曾出现零墙体被写进 Golden expected 的事故——故 wallBlocks&gt;0 不可省略。
     */
    public static void assertB07InnerOffsetDegradation(GoldenBuildingMetrics metrics) {
        // 核心：不能只靠 snapshot；零墙体一旦被刷新进 Expectations 就会永久「通过」回归
        assertTrue(metrics.wallBlocks() > 0,
            "B07: wallBlocks > 0 (solid wall mass required when inner offset fails)");
        assertEquals(0, metrics.floorBlocks(),
            "B07: floorBlocks == 0 (skip interior when inner offset fails)");
        assertTrue(metrics.warnings().contains("plugin.building.warn.inner_offset_failed"),
            "B07: must warn inner_offset_failed");
        assertTrue(metrics.warnings().contains("plugin.building.warn.roof_downgrade"),
            "B07: must warn roof_downgrade");
        assertEquals("FLAT", metrics.effectiveRoofType(),
            "B07: sloped roof must downgrade to FLAT");
    }

    public static void assertB10ThickWallInvariants(
            GoldenBuildingMetrics thick,
            GoldenBuildingMetrics thin) {
        assertTrue(thick.wallBlocks() > thin.wallBlocks(), "thick wall must exceed thin wall block count");
        assertTrue(thick.wallBlocks() > thick.floorBlocks(), "thick wall mass should dominate floor slabs");
        assertTrue(thick.minX() >= 0, "openings must carve inward; minX must stay in footprint");
        assertTrue(thick.minZ() >= 0, "openings must carve inward; minZ must stay in footprint");
        assertTrue(thick.maxX() < 10, "blocks use cell centers; maxX stays within [0,10)");
        assertTrue(thick.maxZ() < 8, "blocks use cell centers; maxZ stays within [0,8)");
        assertTrue(thick.warnings().isEmpty());
    }

    public static void assertB11HasDoorAndWindowOpenings(BuildingFootprint footprint, GoldenBuildingMetrics metrics) {
        long doors = footprint.getOpenings().stream().filter(o -> o.kind() == OpeningKind.DOOR).count();
        long windows = footprint.getOpenings().stream().filter(o -> o.kind() == OpeningKind.WINDOW).count();
        assertTrue(doors > 0 && windows > 0, "B11 fixture must declare door and window");
        assertTrue(metrics.openingBlocks() > 0, "B11 must carve opening air");
    }

    private static long pack(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static boolean isAir(String blockId) {
        return blockId == null
            || blockId.isBlank()
            || "minecraft:air".equals(blockId)
            || "minecraft:cave_air".equals(blockId)
            || "minecraft:void_air".equals(blockId);
    }

    private static String normalize(String blockId) {
        return blockId != null ? blockId.trim() : "";
    }
}
