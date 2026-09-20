package com.plot.plugin.powerline;

import com.plot.core.block.BlockSpec;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConductorSpanChainContinuityTest {

    @Test
    void flatChainSpanUsesConsistentAxisAlongWire() {
        PowerLineFootprint line = WireTestSupport.horizontalLine(40.0);
        line.setWireMaterial(MaterialMix.single("minecraft:chain"));
        line.setSagRatio(0.0);

        PowerLineGenerationResult result = PowerLineGeneratorWireTest.generate(line);
        int wireY = 64 + 10;
        List<String> axes = result.placementRecords.values().stream()
            .filter(record -> record.pos.getY() == wireY)
            .filter(record -> "minecraft:chain".equals(BlockSpec.parse(record.newBlockId).blockId()))
            .sorted(Comparator.comparingInt(record -> record.pos.getX()))
            .map(record -> BlockSpec.parse(record.newBlockId).property("axis"))
            .toList();

        assertTrue(axes.size() >= 5, "expected a continuous chain span");
        assertEquals(1, axes.stream().distinct().count(), "flat span chains should share one axis");
    }
}
