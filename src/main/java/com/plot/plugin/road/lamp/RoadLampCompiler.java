package com.plot.plugin.road.lamp;

import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The preset library, designer and road placement all consume this compiler's voxels. */
public final class RoadLampCompiler {
    private static final int MAX_CACHED_STYLES = 64;
    private static final Map<RoadLampStyle, List<Voxel>> CACHE = new LinkedHashMap<>(16, 0.75f, true);

    private RoadLampCompiler() {
    }

    public record Voxel(BlockPos position, String block) {
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
        var material = style.material();
        for (int y = 0; y < style.height(); y++) {
            blocks.put(new BlockPos(0, y, 0), material.pole);
        }
        if (style.plinth()) {
            blocks.put(BlockPos.ORIGIN, material.trim);
            if (style.detail() == RoadLampStyle.Detail.HERITAGE) blocks.put(BlockPos.ORIGIN.up(), material.beam);
        }
        for (var level : style.levels()) {
            int y = level.y();
            blocks.put(new BlockPos(0, y, 0), material.trim);
            if (style.detail() == RoadLampStyle.Detail.HERITAGE) {
                blocks.replace(new BlockPos(0, y - 1, 0), material.pole, material.beam);
            }
            if (style.detail() == RoadLampStyle.Detail.EAVES && level.mount() == RoadLampStyle.Mount.HOOD) {
                blocks.putIfAbsent(new BlockPos(0, y + 1, 0), material.beam);
            }
            switch (level.layout()) {
                case SINGLE -> emitHead(blocks, level, material, style.detail(), 0, 0);
                case INWARD -> emitHead(blocks, level, material, style.detail(), 1, 0);
                case OUTWARD -> emitHead(blocks, level, material, style.detail(), -1, 0);
                case TWIN, TRIPLE, CROSS -> {
                    emitHead(blocks, level, material, style.detail(), 1, 0);
                    emitHead(blocks, level, material, style.detail(), -1, 0);
                    if (level.layout() == RoadLampStyle.Layout.TRIPLE) {
                        emitHead(blocks, level, material, style.detail(), 0, 0);
                    }
                    if (level.layout() == RoadLampStyle.Layout.CROSS) {
                        emitHead(blocks, level, material, style.detail(), 0, 1);
                        emitHead(blocks, level, material, style.detail(), 0, -1);
                    }
                }
            }
        }
        return blocks.entrySet().stream().map(entry -> new Voxel(entry.getKey(), entry.getValue())).toList();
    }

    private static void emitHead(Map<BlockPos, String> blocks, RoadLampStyle.Level level,
                                 RoadLampStyle.Material material, RoadLampStyle.Detail detail, int dx, int dz) {
        int reach = dx == 0 && dz == 0 ? 0 : level.reach();
        int y = level.y();
        for (int i = 1; i <= reach; i++) {
            String beam = detail == RoadLampStyle.Detail.URBAN && i < reach
                ? material.slabTop() : material.beam;
            blocks.put(new BlockPos(i * dx, y, i * dz), beam);
            if (detail == RoadLampStyle.Detail.EAVES && level.mount() == RoadLampStyle.Mount.HOOD) {
                var ridge = new BlockPos(i * dx, y + 1, i * dz);
                blocks.put(ridge, material.beam);
                blocks.putIfAbsent(ridge.add(-dz, 0, dx), material.slabBottom());
                blocks.putIfAbsent(ridge.add(dz, 0, -dx), material.slabBottom());
            }
        }
        BlockPos tip = new BlockPos(reach * dx, y, reach * dz);
        switch (level.mount()) {
            case TOP -> blocks.put(tip.up(), level.light().block(false));
            case CHAIN -> {
                for (int i = 1; i <= level.chain(); i++) {
                    blocks.put(tip.down(i), "minecraft:iron_chain[axis=y]");
                }
                blocks.put(tip.down(level.chain() + 1), level.light().block(true));
                if (detail == RoadLampStyle.Detail.TIMBER) {
                    blocks.putIfAbsent(tip.add(dx, 0, dz), material.slabTop());
                    if (reach > 1) blocks.putIfAbsent(new BlockPos(dx, y - 1, dz), material.trim);
                }
            }
            case HOOD -> {
                blocks.put(tip.down(), level.light().block(true));
                blocks.put(tip.up(), material.trim);
                blocks.put(tip.add(-dz, 0, dx), material.trim);
                blocks.put(tip.add(dz, 0, -dx), material.trim);
            }
        }
    }

    public static List<Voxel> position(List<Voxel> local, BlockPos anchor, int inwardX, int inwardZ) {
        return local.stream().map(voxel -> {
            BlockPos p = voxel.position();
            return new Voxel(anchor.add(
                p.getX() * inwardX - p.getZ() * inwardZ, p.getY(),
                p.getX() * inwardZ + p.getZ() * inwardX), voxel.block());
        }).toList();
    }
}
