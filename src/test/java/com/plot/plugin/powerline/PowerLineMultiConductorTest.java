package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.design.ConductorAttachmentPresets;
import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.design.PoleLayer;
import com.plot.plugin.powerline.model.PowerLineDesignProject;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineMultiConductorTest {

    @Test
    void threeAttachmentsProduceThreeConductors() {
        PowerLineFootprint line = horizontalLine(40.0);
        line.setPoleDesignId("three-phase");

        PowerLineDesignProject designs = new PowerLineDesignProject();
        designs.addDesign(threePhaseDesign());

        PowerLineGenerationResult result = generate(line, designs, flatTerrain(64));

        Set<Integer> wireZs = wireZValuesNearMidSpan(result, 64 + 10, 20);
        assertTrue(wireZs.contains(-3), "missing phase A conductor at z=-3");
        assertTrue(wireZs.contains(0), "missing phase B conductor at z=0");
        assertTrue(wireZs.contains(3), "missing phase C conductor at z=+3");
    }

    @Test
    void conductorsDoNotCollapseToCenterline() {
        PowerLineFootprint line = horizontalLine(30.0);
        line.setPoleDesignId("three-phase");

        PowerLineGenerationResult result = generate(
            line,
            designProjectWith(threePhaseDesign()),
            flatTerrain(64));

        Set<Integer> wireZs = wireZValuesNearMidSpan(result, 64 + 10, 15);
        assertTrue(wireZs.size() >= 3);
        assertFalse(wireZs.size() == 1 && wireZs.contains(0));
    }

    @Test
    void duplicateAttachmentIdEmitsWarning() {
        ResolvedAttachment first = attachment("phase_a", "A-first", -3, 64);
        ResolvedAttachment duplicate = attachment("phase_a", "A-duplicate", 0, 64);
        ResolvedAttachment endA = attachment("phase_a", "A", -3, 64);

        PolePlacement start = new PolePlacement(
            new Vec2d(0, 0),
            PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64),
            null,
            List.of(first, duplicate),
            74,
            true);
        PolePlacement end = new PolePlacement(
            new Vec2d(10, 0),
            PoleFrame.fromPole(new Vec2d(10, 0), new Vec2d(1, 0), 64),
            null,
            List.of(endA),
            74,
            true);

        PowerLineFootprint footprint = WireTestSupport.horizontalLine(10.0);
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        ConductorSpanGenerator.generateBetween(
            start,
            end,
            0,
            1,
            "start",
            "end",
            footprint,
            flatTerrain(64),
            result,
            identityCoordinates(),
            projection());

        assertTrue(result.warnings.stream().anyMatch(
            w -> w.contains("plugin.powerline.warn.duplicate_attachment_id")));
    }

    @Test
    void attachmentMatchingUsesIdNotIndex() {
        ResolvedAttachment startA = attachment("phase_a", "A", -3, 64);
        ResolvedAttachment startB = attachment("phase_b", "B", 0, 64);
        ResolvedAttachment endA = attachment("phase_a", "A", -3, 64);
        ResolvedAttachment endC = attachment("phase_c", "C", 3, 64);

        PolePlacement start = new PolePlacement(
            new Vec2d(0, 0),
            PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), 64),
            null,
            List.of(startA, startB),
            74,
            true);
        PolePlacement end = new PolePlacement(
            new Vec2d(10, 0),
            PoleFrame.fromPole(new Vec2d(10, 0), new Vec2d(1, 0), 64),
            null,
            List.of(endA, endC),
            74,
            true);

        PowerLineFootprint footprint = WireTestSupport.horizontalLine(10.0);
        PowerLineGenerationResult result = new PowerLineGenerationResult(footprint);
        ConductorSpanGenerator.generateBetween(
            start,
            end,
            0,
            1,
            "start",
            "end",
            footprint,
            flatTerrain(64),
            result,
            identityCoordinates(),
            projection());

        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("phase_b")));
        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("phase_c")));
    }

    private static ResolvedAttachment attachment(String id, String name, double lateral, int groundY) {
        PoleFrame frame = PoleFrame.fromPole(new Vec2d(0, 0), new Vec2d(1, 0), groundY);
        Vec2d plan = frame.toPlanPoint(lateral, 0.0);
        return new ResolvedAttachment(
            id,
            name,
            com.plot.plugin.powerline.design.AttachmentRole.PHASE_A,
            plan,
            plan.x,
            groundY + 10.0,
            plan.y,
            groundY + 10.0,
            com.plot.core.material.MaterialMix.single("minecraft:iron_bars"),
            0);
    }

    private static ICoordinateService identityCoordinates() {
        return com.plot.test.world.IdentityCoordinateService.INSTANCE;
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

    private static TerrainSampler flatTerrain(int y) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(com.plot.api.geometry.Vec2d planPoint) {
                return y;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };
    }

    @Test
    void differentTowerGroundHeightsStillConnect() {
        PowerLineFootprint line = horizontalLine(20.0);
        line.setPoleDesignId("three-phase");

        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return planPoint.x > 10 ? 70 : 64;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }
        };
        PowerLineGenerationResult result = generate(
            line,
            designProjectWith(threePhaseDesign()),
            terrain);

        assertFalse(result.placementRecords.isEmpty());
        assertTrue(result.wireLength > 0.0);
    }

    @Test
    void cornerPolePreservesAttachmentIdentity() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(20, 0),
            new Vec2d(20, 20)));
        line.setMaxPoleSpacing(50.0);
        line.setPoleDesignId("three-phase");

        PowerLineGenerationResult result = generate(
            line,
            designProjectWith(threePhaseDesign()),
            flatTerrain(64));

        Set<Integer> wireZs = collectAllWireZ(result, 64 + 10);
        assertTrue(wireZs.contains(-3));
        assertTrue(wireZs.contains(0));
        assertTrue(wireZs.contains(3));
    }

    @Test
    void generatedConductorsRemainVoxelContinuous() {
        PowerLineFootprint line = horizontalLine(20.0);
        line.setPoleDesignId("three-phase");

        PowerLineGenerationResult result = generate(
            line,
            designProjectWith(threePhaseDesign()),
            flatTerrain(64));

        for (int z : List.of(-3, 0, 3)) {
            WireTestSupport.assertHorizontalWireCoversX(result, 64 + 10, 0, 20, z);
        }
    }

    private static PoleDesign threePhaseDesign() {
        PoleDesign design = new PoleDesign("three-phase", "Three Phase");
        design.addLayer(new PoleLayer(
            PoleLayer.Shape.COLUMN,
            8,
            MaterialMix.single("minecraft:oak_fence")));
        design.setAttachments(ConductorAttachmentPresets.threePhaseHorizontal(10.0));
        return design;
    }

    private static PowerLineFootprint horizontalLine(double length) {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(length, 0)));
        line.setMaxPoleSpacing(100.0);
        line.setSagRatio(0.0);
        return line;
    }

    private static PowerLineDesignProject designProjectWith(PoleDesign design) {
        PowerLineDesignProject project = new PowerLineDesignProject();
        project.addDesign(design);
        return project;
    }

    private static PowerLineGenerationResult generate(
            PowerLineFootprint line,
            PowerLineDesignProject designs,
            TerrainSampler terrain) {
        return new PowerLineGenerator(identityCoordinates(), projection()).generate(
            line,
            terrain,
            new PoleDesignResolver(designs));
    }

    private static Set<Integer> wireZValuesNearMidSpan(PowerLineGenerationResult result, int wireY, int midX) {
        Set<Integer> zs = new HashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            if (record.pos.getY() == wireY && Math.abs(record.pos.getX() - midX) <= 2) {
                if ("minecraft:iron_bars".equals(record.newBlockId)) {
                    zs.add(record.pos.getZ());
                }
            }
        }
        return zs;
    }

    private static Set<Integer> collectAllWireZ(PowerLineGenerationResult result, int wireY) {
        Set<Integer> zs = new HashSet<>();
        for (BlockRecord record : result.placementRecords.values()) {
            if (record.pos.getY() == wireY
                    && "minecraft:iron_bars".equals(record.newBlockId)) {
                zs.add(record.pos.getZ());
            }
        }
        return zs;
    }
}
