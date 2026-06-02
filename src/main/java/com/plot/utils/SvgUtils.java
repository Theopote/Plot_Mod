package com.plot.utils;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * SVG 工具：将 SVG 资源栅格化为 BufferedImage，供 OpenGL 纹理上传使用。
 */
public final class SvgUtils {
    private SvgUtils() {
    }

    public static BufferedImage readSvg(InputStream inputStream) throws IOException {
        try {
            BufferedImageTranscoder transcoder = new BufferedImageTranscoder();
            TranscoderInput input = new TranscoderInput(inputStream);
            transcoder.transcode(input, (TranscoderOutput) null);

            BufferedImage image = transcoder.getImage();
            if (image == null) {
                throw new IOException("Failed to rasterize SVG: image is null");
            }
            return image;
        } catch (TranscoderException e) {
            throw new IOException("Failed to parse SVG resource", e);
        }
    }

    private static final class BufferedImageTranscoder extends ImageTranscoder {
        private BufferedImage image;

        @Override
        public BufferedImage createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage image, TranscoderOutput output) {
            this.image = image;
        }

        public BufferedImage getImage() {
            return image;
        }
    }
}