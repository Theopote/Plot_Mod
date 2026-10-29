package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.station.RoadStationingReadCache;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RoadVoxelProfileCompileJobTest {
    private final RoadSystemConfig config = new RoadSystemConfig("profile-job");

    @Test
    void yieldsAtBudgetAndAssemblesOnASeparateTickWithIdenticalHeights() {
        var source = network(3);
        String original = source.toJson();
        var frozen = source.snapshot();
        var clock = new AtomicLong();
        var generator = new CountingGenerator(clock);
        var job = job(frozen, generator, clock);
        job.advance(5);
        assertEquals(RoadVoxelProfileCompileJob.Phase.SAMPLING, job.phase());
        assertEquals(0, generator.calls);
        job.advance(5);
        assertEquals(1, job.completedEdges());
        assertFalse(job.isDone());
        job.advance(5);
        assertEquals(2, job.completedEdges());
        job.advance(5);
        assertEquals(RoadVoxelProfileCompileJob.Phase.ASSEMBLING, job.phase());
        assertThrows(IllegalStateException.class, job::result);
        job.advance(5);
        assertEquals(RoadVoxelProfileCompileJob.Phase.COMPLETED, job.phase());
        var expectedNetwork = source.snapshot();
        var expected = RoadVoxelProfileCompiler.compile(expectedNetwork, expectedNetwork.getRoad("road"),
            config, new RoadGenerator(config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()),
            new FlatTerrainSampler(60), Map.of(), ProfileCompileOptions.defaults());
        assertTrue(job.result().feasible());
        assertEquals(expected.samples(), job.result().samples());
        assertEquals(original, source.toJson());
        assertFalse(RoadStationingReadCache.isActiveFor(frozen));
    }

    @Test
    void confirmedCurvedProfileKeepsTheSameSamplesAcrossFrameBoundaries() {
        var source = network(5);
        var road = source.getRoad("road");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setFlatVerticalIntent(null);
        road.setMaxSlope(20f);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.withCurve(50, 69, 20),
            PointOfVerticalIntersection.of(100, 66))));
        com.plot.plugin.road.vertical.ResolvedProfileAuthority.markEditedProfileConfirmed(source, road);
        var clock = new AtomicLong();
        var frozen = source.snapshot();
        var job = job(frozen, new CountingGenerator(clock), clock);
        while (!job.isDone()) job.advance(5);

        var expectedNetwork = source.snapshot();
        var expected = RoadVoxelProfileCompiler.compile(expectedNetwork, expectedNetwork.getRoad("road"),
            config, new RoadGenerator(config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()),
            new FlatTerrainSampler(60), Map.of(), ProfileCompileOptions.defaults());
        assertTrue(job.result().feasible(), job.result().failureReason());
        assertEquals(expected.samples(), job.result().samples());
        assertTrue(job.result().samples().stream().mapToInt(BuildHeightSample::buildY).max().orElseThrow() > 66);
    }

    @Test
    void cancellationStopsFurtherTerrainReadsAndNeverProducesPartialSuccess() {
        var clock = new AtomicLong();
        var generator = new CountingGenerator(clock);
        var job = job(network(5), generator, clock);
        job.advance(5);
        job.advance(5);
        assertEquals(1, generator.calls);
        job.cancel();
        job.advance(Long.MAX_VALUE);
        assertEquals(1, generator.calls);
        assertEquals(RoadVoxelProfileCompileJob.Phase.CANCELLED, job.phase());
        assertThrows(IllegalStateException.class, job::result);
    }

    @Test
    void missingSegmentSamplesFailInsteadOfPublishingAHalfRoad() {
        var clock = new AtomicLong();
        var generator = new CountingGenerator(clock);
        generator.failAt = 2;
        var job = job(network(3), generator, clock);
        job.advance(5);
        job.advance(1000);
        assertEquals(RoadVoxelProfileCompileJob.Phase.FAILED, job.phase());
        assertFalse(job.result().feasible());
        assertFalse(job.result().hasSamples());
    }

    @Test
    void cheapSamplesStillHaveAnEdgeLimitPerTick() {
        var clock = new AtomicLong();
        var generator = new CountingGenerator(clock);
        var job = job(network(8), generator, clock);
        job.advance(Long.MAX_VALUE);
        job.advance(Long.MAX_VALUE);
        assertEquals(4, job.completedEdges());
        assertFalse(job.isDone());
    }

    @Test
    void phaseTimingTracksPreparingSamplingAndAssemblingSeparately() {
        var clock = new AtomicLong();
        var generator = new CountingGenerator(clock);
        var job = job(network(5), generator, clock);
        while (!job.isDone()) {
            job.advance(5);
        }
        var timing = job.phaseTiming();
        // Fake clock only advances inside sampleEdgeProfile; prepare/assemble may be 0 here.
        assertTrue(timing.preparingNanos() >= 0, "prepare timing is always recorded");
        assertTrue(timing.samplingNanos() > 0, "sampling must record elapsed nanos");
        assertTrue(timing.assemblingNanos() >= 0, "assemble timing is recorded even if free");
        assertTrue(timing.maxEdgeSampleNanos() > 0, "per-edge sample cost must be tracked");
        assertEquals(5, timing.sampledEdges());
        assertTrue(timing.samplingTicks() >= 2, "5 edges with budget 5 need multiple sampling ticks");
        assertEquals(timing.preparingNanos() + timing.samplingNanos() + timing.assemblingNanos(),
            timing.totalNanos());
    }

    @Test
    void longRoadRealClockBaselineRecordsAllThreePhases() {
        // Measure-first baseline: do not assert hard budgets; only that phases are timed.
        var frozen = network(12);
        var job = new RoadVoxelProfileCompileJob(
            frozen,
            frozen.getRoad("road"),
            config,
            new RoadGenerator(config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()),
            new FlatTerrainSampler(60),
            Map.of(),
            ProfileCompileOptions.defaults(),
            null,
            null);
        while (!job.isDone()) {
            job.advance(8_000_000L);
        }
        assertEquals(RoadVoxelProfileCompileJob.Phase.COMPLETED, job.phase());
        var timing = job.phaseTiming();
        assertEquals(12, timing.sampledEdges());
        assertTrue(timing.preparingNanos() > 0, "real prepare work should take time");
        assertTrue(timing.samplingNanos() > 0, "real sampling should take time");
        assertTrue(timing.assemblingNanos() > 0, "real assemble should take time");
        assertTrue(timing.maxEdgeSampleNanos() > 0);
        assertTrue(timing.samplingTicks() >= 3, "12 edges / 4 per tick needs >=3 sampling ticks");
    }

    @Test
    void explicitDraftIsUsedForSamplingWithoutChangingTheSavedRoad() {
        var source = network(2);
        var savedRoad = source.getRoad("road");
        savedRoad.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        savedRoad.setFlatVerticalIntent(null);
        savedRoad.setVerticalAlignment(null);
        var frozen = source.snapshot();
        var clock = new AtomicLong();
        var override = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 80), PointOfVerticalIntersection.of(40, 80)));
        var job = new RoadVoxelProfileCompileJob(frozen, frozen.getRoad("road"), config,
            new CountingGenerator(clock), new FlatTerrainSampler(60), Map.of(),
            ProfileCompileOptions.defaults(), null, override, clock::get);
        while (!job.isDone()) job.advance(1000);
        assertTrue(job.result().feasible(), job.result().failureReason());
        assertTrue(job.result().samples().stream().allMatch(s -> s.buildY() == 80));
        assertNull(savedRoad.getVerticalAlignment());
        assertEquals(com.plot.plugin.road.vertical.ProfileSource.AUTO, savedRoad.getEffectiveProfileSource());
    }

    private RoadVoxelProfileCompileJob job(RoadNetwork frozen, CountingGenerator generator, AtomicLong clock) {
        return new RoadVoxelProfileCompileJob(frozen, frozen.getRoad("road"), config, generator,
            new FlatTerrainSampler(60), Map.of(), ProfileCompileOptions.defaults(), null, null, clock::get);
    }

    private RoadNetwork network(int edges) {
        var network = new RoadNetwork();
        var road = network.createRoad("road");
        road.setVerticalMode(RoadVerticalMode.FLAT);
        road.setFlatVerticalIntent(new FlatVerticalIntent(64));
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64), PointOfVerticalIntersection.of(edges * 20, 64))));
        var previous = network.createNode(new Vec2d(0, 0));
        for (int i = 1; i <= edges; i++) {
            var next = network.createNode(new Vec2d(i * 20, 0));
            network.createEdge(previous.getId(), next.getId(),
                List.of(previous.getPosition(), next.getPosition()), road.getId());
            previous = next;
        }
        return network;
    }

    private final class CountingGenerator extends RoadGenerator {
        private final AtomicLong clock;
        private int calls;
        private int failAt = -1;

        CountingGenerator(AtomicLong clock) {
            super(config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
            this.clock = clock;
        }

        @Override
        public RoadGenerationResult sampleEdgeProfile(RoadNetwork network, RoadEdge edge, RoadNode start,
                RoadNode end, TerrainSampler terrain, Map<String, Integer> elevations) {
            assertTrue(RoadStationingReadCache.isActiveFor(network));
            calls++;
            clock.addAndGet(20);
            return calls == failAt ? null : super.sampleEdgeProfile(network, edge, start, end, terrain, elevations);
        }
    }
}
