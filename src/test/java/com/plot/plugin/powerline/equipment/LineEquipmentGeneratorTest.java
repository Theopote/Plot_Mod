package com.plot.plugin.powerline.equipment;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.PoleFrame;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.ResolvedAttachment;
import com.plot.plugin.powerline.design.AttachmentRole;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineEquipmentGeneratorTest {

    @Test
    void suspensionInsulatorVertical() {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        ResolvedAttachment attachment = attachment(64, 3, InsulatorType.SUSPENSION);

        LineEquipmentGenerator.place(attachment, frame(), footprint, result, projection());

        Set<Integer> ys = yValuesAt(result, 0, 0);
        assertTrue(ys.contains(64));
        assertTrue(ys.contains(65));
        assertTrue(ys.contains(66));
    }

    @Test
    void strainInsulatorFollowsPoleFrame() {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        ResolvedAttachment attachment = attachment(64, 3, InsulatorType.STRAIN);

        LineEquipmentGenerator.place(attachment, frame(), footprint, result, projection());

        Set<Integer> xs = xValuesAt(result, 67, 0);
        assertTrue(xs.contains(-3) || xs.contains(-2) || xs.contains(-1));
        assertTrue(xs.contains(0));
    }

    @Test
    void twinColumnPlacesParallelInsulators() {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        ResolvedAttachment attachment = new ResolvedAttachment(
            "a",
            "A",
            AttachmentRole.PHASE_A,
            new Vec2d(0, 0),
            0,
            68,
            0,
            64,
            MaterialMix.single("minecraft:iron_bars"),
            4,
            InsulatorType.VERTICAL,
            InsulatorMountStyle.TWIN_COLUMN);

        LineEquipmentGenerator.place(attachment, frame(), footprint, result, projection());

        assertTrue(result.placementRecords.containsKey(new BlockPos(0, 64, 0)));
        assertTrue(result.placementRecords.containsKey(new BlockPos(0, 64, 1)));
    }

    @Test
    void vPairPlacesDiagonalLegs() {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        ResolvedAttachment attachment = new ResolvedAttachment(
            "a",
            "A",
            AttachmentRole.PHASE_A,
            new Vec2d(0, 0),
            0,
            70,
            0,
            64,
            MaterialMix.single("minecraft:iron_bars"),
            5,
            InsulatorType.SUSPENSION,
            InsulatorMountStyle.V_PAIR);

        LineEquipmentGenerator.place(attachment, frame(), footprint, result, projection());

        assertTrue(result.placementRecords.containsKey(new BlockPos(0, 64, -1)));
        assertTrue(result.placementRecords.containsKey(new BlockPos(0, 64, 1)));
        assertTrue(result.placementRecords.containsKey(new BlockPos(0, 70, 0)));
    }

    @Test
    void legacyInsulatorStillWorks() {
        PowerLineFootprint footprint = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        ResolvedAttachment attachment = new ResolvedAttachment(
            "a",
            "A",
            AttachmentRole.PHASE_A,
            new Vec2d(0, 0),
            0,
            67,
            0,
            64,
            MaterialMix.single("minecraft:iron_bars"),
            3);

        LineEquipmentGenerator.place(attachment, frame(), footprint, result, projection());
        assertFalse(result.placementRecords.isEmpty());
    }

    private static PoleFrame frame() {
        return PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64);
    }

    private static ResolvedAttachment attachment(int groundY, int length, InsulatorType type) {
        return new ResolvedAttachment(
            "a",
            "A",
            AttachmentRole.PHASE_A,
            new Vec2d(0, 0),
            0,
            groundY + length,
            0,
            groundY,
            MaterialMix.single("minecraft:iron_bars"),
            length,
            type);
    }

    private static Set<Integer> yValuesAt(PowerLineGenerationResult result, int x, int z) {
        Set<Integer> ys = new HashSet<>();
        for (var record : result.placementRecords.values()) {
            if (record.pos.getX() == x && record.pos.getZ() == z) {
                ys.add(record.pos.getY());
            }
        }
        return ys;
    }

    private static Set<Integer> xValuesAt(PowerLineGenerationResult result, int y, int z) {
        Set<Integer> xs = new HashSet<>();
        for (var record : result.placementRecords.values()) {
            if (record.pos.getY() == y && record.pos.getZ() == z) {
                xs.add(record.pos.getX());
            }
        }
        return xs;
    }

    private static IBlockProjectionService projection() {
        return new IBlockProjectionService() {
            @Override
            public String getBlockIdAt(BlockPos pos) {
                return "minecraft:air";
            }

            @Override
            public boolean setBlockAt(BlockPos pos, String blockId) {
                return true;
            }

            @Override
            public PlacementReadiness checkWorldModificationReadiness() {
                return PlacementReadiness.ok();
            }
        };
    }
}
