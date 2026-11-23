package com.plot.plugin.road.lamp;

import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The preset library, designer and road placement all consume this compiler's voxels. */
public final class RoadLampCompiler {
    private static final int MAX_CACHED_STYLES = 64;
    private static final Map<RoadLampStyle, List<Voxel>> CACHE = new LinkedHashMap<>(16, 0.75f, true);

    private RoadLampCompiler() {
    }

    /**
     * @param levelIndex 所属灯具层（0-based）；杆身/底座等非层体素为 -1
     */
    public record Voxel(BlockPos position, String block, int levelIndex) {
        public Voxel(BlockPos position, String block) {
            this(position, block, -1);
        }
    }

    public static synchronized List<Voxel> compile(RoadLampStyle style) {
        var voxels = CACHE.computeIfAbsent(style, RoadLampCompiler::compileStructure);
        if (CACHE.size() > MAX_CACHED_STYLES) {
            CACHE.remove(CACHE.keySet().iterator().next());
        }
        return voxels;
    }

    private static List<Voxel> compileStructure(RoadLampStyle style) {
        Map<BlockPos, String> blocks = new LinkedHashMap<>();
        Map<BlockPos, Integer> levelOf = new HashMap<>();
        var material = style.material();
        for (int y = 0; y < style.height(); y++) {
            blocks.put(new BlockPos(0, y, 0), material.pole);
        }
        if (style.plinth()) {
            blocks.put(BlockPos.ORIGIN, material.trim);
            if (style.detail() == RoadLampStyle.Detail.HERITAGE) blocks.put(BlockPos.ORIGIN.up(), material.beam);
        }
        List<RoadLampStyle.Level> levels = style.levels();
        for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
            var level = levels.get(levelIndex);
            int y = level.y();
            blocks.put(new BlockPos(0, y, 0), material.trim);
            if (style.detail() == RoadLampStyle.Detail.HERITAGE) {
                blocks.replace(new BlockPos(0, y - 1, 0), material.pole, material.beam);
            }
            if (style.detail() == RoadLampStyle.Detail.EAVES && level.mount() == RoadLampStyle.Mount.HOOD) {
                blocks.putIfAbsent(new BlockPos(0, y + 1, 0), material.beam);
            }
            switch (level.layout()) {
                case SINGLE -> emitHead(blocks, levelOf, level, material, style.detail(), 0, 0, levelIndex);
                case INWARD -> emitHead(blocks, levelOf, level, material, style.detail(), 1, 0, levelIndex);
                case OUTWARD -> emitHead(blocks, levelOf, level, material, style.detail(), -1, 0, levelIndex);
                case TWIN, TRIPLE, CROSS -> {
                    emitHead(blocks, levelOf, level, material, style.detail(), 1, 0, levelIndex);
                    emitHead(blocks, levelOf, level, material, style.detail(), -1, 0, levelIndex);
                    if (level.layout() == RoadLampStyle.Layout.TRIPLE) {
                        emitHead(blocks, levelOf, level, material, style.detail(), 0, 0, levelIndex);
                    }
                    if (level.layout() == RoadLampStyle.Layout.CROSS) {
                        emitHead(blocks, levelOf, level, material, style.detail(), 0, 1, levelIndex);
                        emitHead(blocks, levelOf, level, material, style.detail(), 0, -1, levelIndex);
                    }
                }
            }
        }
        return blocks.entrySet().stream()
            .map(entry -> new Voxel(entry.getKey(), entry.getValue(), levelOf.getOrDefault(entry.getKey(), -1)))
            .toList();
    }

    private static void emitHead(Map<BlockPos, String> blocks, Map<BlockPos, Integer> levelOf,
                                 RoadLampStyle.Level level, RoadLampStyle.Material material,
                                 RoadLampStyle.Detail detail, int dx, int dz, int levelIndex) {
        int reach = dx == 0 && dz == 0 ? 0 : level.reach();
        int y = level.y();
        for (int i = 1; i <= reach; i++) {
            String beam = detail == RoadLampStyle.Detail.URBAN && i < reach
                ? material.slabTop() : material.beam;
            putLevel(blocks, levelOf, new BlockPos(i * dx, y, i * dz), beam, levelIndex);
            if (detail == RoadLampStyle.Detail.EAVES && level.mount() == RoadLampStyle.Mount.HOOD) {
                var ridge = new BlockPos(i * dx, y + 1, i * dz);
                putLevel(blocks, levelOf, ridge, material.beam, levelIndex);
                putLevelIfAbsent(blocks, levelOf, ridge.add(-dz, 0, dx), material.slabBottom(), levelIndex);
                putLevelIfAbsent(blocks, levelOf, ridge.add(dz, 0, -dx), material.slabBottom(), levelIndex);
            }
        }
        BlockPos tip = new BlockPos(reach * dx, y, reach * dz);
        switch (level.mount()) {
            case TOP -> putLevel(blocks, levelOf, tip.up(), level.light().block(false), levelIndex);
            case CHAIN -> {
                for (int i = 1; i <= level.chain(); i++) {
                    putLevel(blocks, levelOf, tip.down(i), "minecraft:iron_chain[axis=y]", levelIndex);
                }
                putLevel(blocks, levelOf, tip.down(level.chain() + 1), level.light().block(true), levelIndex);
                if (detail == RoadLampStyle.Detail.TIMBER) {
                    putLevelIfAbsent(blocks, levelOf, tip.add(dx, 0, dz), material.slabTop(), levelIndex);
                    if (reach > 1) {
                        putLevelIfAbsent(blocks, levelOf, new BlockPos(dx, y - 1, dz), material.trim, levelIndex);
                    }
                }
            }
            case HOOD -> {
                putLevel(blocks, levelOf, tip.down(), level.light().block(true), levelIndex);
                putLevel(blocks, levelOf, tip.up(), material.trim, levelIndex);
                putLevel(blocks, levelOf, tip.add(-dz, 0, dx), material.trim, levelIndex);
                putLevel(blocks, levelOf, tip.add(dz, 0, -dx), material.trim, levelIndex);
            }
        }
    }

    private static void putLevel(Map<BlockPos, String> blocks, Map<BlockPos, Integer> levelOf,
                                 BlockPos pos, String block, int levelIndex) {
        blocks.put(pos, block);
        levelOf.put(pos, levelIndex);
    }

    private static void putLevelIfAbsent(Map<BlockPos, String> blocks, Map<BlockPos, Integer> levelOf,
                                         BlockPos pos, String block, int levelIndex) {
        if (blocks.putIfAbsent(pos, block) == null) {
            levelOf.putIfAbsent(pos, levelIndex);
        }
    }

    public static List<Voxel> position(List<Voxel> local, BlockPos anchor, int inwardX, int inwardZ) {
        return local.stream().map(voxel -> {
            BlockPos p = voxel.position();
            return new Voxel(anchor.add(
                p.getX() * inwardX - p.getZ() * inwardZ, p.getY(),
                p.getX() * inwardZ + p.getZ() * inwardX), voxel.block(), voxel.levelIndex());
        }).toList();
    }
}
