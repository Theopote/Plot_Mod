package com.plot.plugin.powerline.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainFitServiceStatusTest {

    @Test
    void visualConflictHintIsNonBlockingMessage() {
        assertEquals(
            com.plot.utils.PlotI18n.tr("plugin.powerline.terrain.near_terrain_hint"),
            TerrainFitService.visualConflictHint());
    }
}
