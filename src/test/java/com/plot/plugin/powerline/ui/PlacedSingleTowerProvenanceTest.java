package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.model.PlacedSingleTower;
import com.plot.plugin.powerline.model.PlacedSingleTowerStyleResolver;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.powerline.model.SingleTowerStyleFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.utils.PlotI18n;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacedSingleTowerProvenanceTest {

    @Test
    void resolveStyleNameUsesStoredPresetNotCurrentGlobalStyle() {
        PlacedSingleTower classicTower = standaloneTower(
            PowerLineStylePreset.CLASSIC_LATTICE_ID,
            "Classic Lattice");
        PlacedSingleTower monsterTower = standaloneTower(
            PowerLineStylePreset.MONSTER_PYLON_ID,
            "Monster Pylon");

        PowerLinePluginState state = new PowerLinePluginState();
        PowerLineStylePresetCatalog.monsterPylon().apply(state.getSingleTowerStyle());

        assertEquals(
            PlotI18n.tr(PowerLineStylePresetCatalog.classicLattice().getLabelKey()),
            PlacedSingleTowerStyleResolver.resolveStyleName(classicTower, new PowerLineProject()));
        assertEquals(
            PlotI18n.tr(PowerLineStylePresetCatalog.monsterPylon().getLabelKey()),
            PlacedSingleTowerStyleResolver.resolveStyleName(monsterTower, new PowerLineProject()));
    }

    @Test
    void jsonRoundTripPreservesStandaloneProvenance() {
        PowerLineProject project = new PowerLineProject();
        PlacedSingleTower tower = standaloneTower(
            PowerLineStylePreset.CLASSIC_LATTICE_ID,
            "Classic Lattice");
        project.addPlacedSingleTower(tower);

        String json = project.toJson();
        assertTrue(json.contains("\"stylePresetId\""));
        assertTrue(json.contains(PowerLineStylePreset.CLASSIC_LATTICE_ID));

        PowerLineProject restored = PowerLineProject.fromJson(json);
        PlacedSingleTower restoredTower = restored.getPlacedSingleTowers().getFirst();
        assertEquals(PowerLineStylePreset.CLASSIC_LATTICE_ID, restoredTower.getStylePresetId());
        assertEquals(SingleTowerStyleFootprint.STYLE_ID, restoredTower.getStyleLineId());
        assertEquals(TowerRole.SUSPENSION, restoredTower.getTowerRole());

        PowerLinePluginState state = new PowerLinePluginState();
        PowerLineStylePresetCatalog.monsterPylon().apply(state.getSingleTowerStyle());
        assertEquals(
            PlotI18n.tr(PowerLineStylePresetCatalog.classicLattice().getLabelKey()),
            PlacedSingleTowerStyleResolver.resolveStyleName(restoredTower, restored));
    }

    private static PlacedSingleTower standaloneTower(String presetId, String designLabel) {
        return new PlacedSingleTower(
            new Vec2d(10, 20),
            1,
            "design-" + presetId,
            designLabel,
            SingleTowerStyleFootprint.STYLE_ID,
            presetId,
            TowerRole.SUSPENSION,
            List.of(new BlockRecord(new BlockPos(1, 2, 3), "minecraft:air", "minecraft:iron_bars")));
    }
}
