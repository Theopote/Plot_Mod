package com.plot.plugin.pattern.image;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.pattern.model.ImagePatternConfig;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImagePatternResolverTest {

    @Test
    void stretchMapsRegionCornersToImagePixels() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 1, 0xFFECEFF1); // quartz, maps from region min corner
        image.setRGB(1, 0, 0xFFA45729); // orange terracotta, maps from region max corner
        ImagePatternRaster raster = ImagePatternRaster.fromBufferedImage(image);

        ImagePatternConfig config = new ImagePatternConfig();
        config.setImagePath("test.png");
        config.setImageWidth(2);
        config.setImageHeight(2);
        config.setFitMode(ImagePatternConfig.FitMode.STRETCH);
        config.setPaletteBlocks(List.of(
            "minecraft:quartz_block",
            "minecraft:orange_terracotta"
        ));

        BlockColorMatcher matcher = new BlockColorMatcher(config.getPaletteBlocks());
        PolygonRegionUtils.RectBounds bounds = new PolygonRegionUtils.RectBounds(0, 2, 0, 2);

        String bottomLeft = ImagePatternResolver.resolveBlockId(
            config, raster, matcher, new Vec2d(0.5, 0.5), bounds);
        String topRight = ImagePatternResolver.resolveBlockId(
            config, raster, matcher, new Vec2d(1.5, 1.5), bounds);

        assertEquals("minecraft:quartz_block", bottomLeft);
        assertEquals("minecraft:orange_terracotta", topRight);
    }

    @Test
    void transparentPixelsAreSkipped() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x00FF0000);
        ImagePatternRaster raster = ImagePatternRaster.fromBufferedImage(image);

        ImagePatternConfig config = new ImagePatternConfig();
        config.setImagePath("transparent.png");
        config.setImageWidth(1);
        config.setImageHeight(1);
        config.setPaletteBlocks(List.of("minecraft:red_wool", "minecraft:blue_wool"));

        BlockColorMatcher matcher = new BlockColorMatcher(config.getPaletteBlocks());
        PolygonRegionUtils.RectBounds bounds = new PolygonRegionUtils.RectBounds(0, 1, 0, 1);

        assertNull(ImagePatternResolver.resolveBlockId(
            config, raster, matcher, new Vec2d(0.5, 0.5), bounds));
    }
}
