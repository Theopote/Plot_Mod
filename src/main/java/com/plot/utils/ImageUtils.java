package com.plot.utils;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 图像处理工具类
 * 提供图像处理相关的实用方法
 */
public class ImageUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageUtils.class);
    
    /**
     * 判断一个数是否是2的幂次方
     */
    public static boolean isPowerOfTwo(int n) {
        return (n & (n - 1)) == 0 && n > 0;
    }
    
    /**
     * 将尺寸调整为2的幂次方
     * @param n 原始尺寸
     * @return 调整后的尺寸
     */
    public static int getNextPowerOfTwo(int n) {
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return n + 1;
    }
    
    /**
     * 将图像调整为2的幂次方尺寸
     * @param originalImage 原始图像
     * @return 调整后的图像
     */
    public static BufferedImage resizeToPowerOfTwo(BufferedImage originalImage) {
        int origWidth = originalImage.getWidth();
        int origHeight = originalImage.getHeight();
        
        // 检查尺寸是否已经是2的幂次方
        if (isPowerOfTwo(origWidth) && isPowerOfTwo(origHeight)) {
            return originalImage;
        }
        
        // 计算新尺寸
        int newWidth = getNextPowerOfTwo(origWidth);
        int newHeight = getNextPowerOfTwo(origHeight);
        
        LOGGER.info("调整图像尺寸: {}x{} -> {}x{}", origWidth, origHeight, newWidth, newHeight);
        
        // 创建新图像
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, originalImage.getType());
        Graphics2D g = resizedImage.createGraphics();
        
        // 将原图绘制到中心位置
        int x = (newWidth - origWidth) / 2;
        int y = (newHeight - origHeight) / 2;
        g.drawImage(originalImage, x, y, origWidth, origHeight, null);
        g.dispose();
        
        return resizedImage;
    }
    
    /**
     * 从输入流读取图像，并确保尺寸为2的幂次方
     * @param is 输入流
     * @return 调整后的图像
     * @throws IOException 如果读取失败
     */
    public static BufferedImage readAndResizeImage(InputStream is) throws IOException {
        BufferedImage originalImage = ImageIO.read(is);
        if (originalImage == null) {
            throw new IOException(PlotI18n.error("error.plot.image.read_failed"));
        }
        
        return resizeToPowerOfTwo(originalImage);
    }
    
    /**
     * 检查并记录图像信息
     * @param imagePath 图像路径
     */
    public static void checkImageInfo(String imagePath) {
        InputStream input = ImageUtils.class.getClassLoader().getResourceAsStream(imagePath);
        String resolvedPath = imagePath;

        if (input == null && imagePath.endsWith(".png")) {
            String svgPath = imagePath.substring(0, imagePath.length() - 4) + ".svg";
            input = ImageUtils.class.getClassLoader().getResourceAsStream(svgPath);
            if (input != null) {
                resolvedPath = svgPath;
                LOGGER.info("图标检查回退: {} -> {}", imagePath, svgPath);
            }
        }

        if (input == null) {
            LOGGER.error("图像文件不存在: {}", imagePath);
            return;
        }

        try (InputStream is = input) {
            BufferedImage image = resolvedPath.endsWith(".svg") ? SvgUtils.readSvg(is) : ImageIO.read(is);
            if (image == null) {
                LOGGER.error("无法解析图像文件: {}", resolvedPath);
                return;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            
            LOGGER.info("图像信息 - 路径: {}, 尺寸: {}x{}, 类型: {}", 
                      resolvedPath, width, height, getImageTypeName(image.getType()));
            
            if (!isPowerOfTwo(width) || !isPowerOfTwo(height)) {
                LOGGER.warn("图像尺寸不是2的幂次方: {}x{}, 建议调整为: {}x{}", 
                          width, height, getNextPowerOfTwo(width), getNextPowerOfTwo(height));
            }
            
        } catch (Exception e) {
            LOGGER.error("检查图像信息时发生错误 {}: {}", imagePath, e.getMessage());
        }
    }
    
    /**
     * 获取图像类型名称
     * @param type 图像类型代码
     * @return 类型名称
     */
    private static String getImageTypeName(int type) {
        return switch (type) {
            case BufferedImage.TYPE_INT_ARGB -> "TYPE_INT_ARGB";
            case BufferedImage.TYPE_INT_ARGB_PRE -> "TYPE_INT_ARGB_PRE";
            case BufferedImage.TYPE_INT_RGB -> "TYPE_INT_RGB";
            case BufferedImage.TYPE_INT_BGR -> "TYPE_INT_BGR";
            case BufferedImage.TYPE_3BYTE_BGR -> "TYPE_3BYTE_BGR";
            case BufferedImage.TYPE_4BYTE_ABGR -> "TYPE_4BYTE_ABGR";
            case BufferedImage.TYPE_4BYTE_ABGR_PRE -> "TYPE_4BYTE_ABGR_PRE";
            case BufferedImage.TYPE_USHORT_565_RGB -> "TYPE_USHORT_565_RGB";
            case BufferedImage.TYPE_USHORT_555_RGB -> "TYPE_USHORT_555_RGB";
            case BufferedImage.TYPE_BYTE_GRAY -> "TYPE_BYTE_GRAY";
            case BufferedImage.TYPE_USHORT_GRAY -> "TYPE_USHORT_GRAY";
            case BufferedImage.TYPE_BYTE_BINARY -> "TYPE_BYTE_BINARY";
            case BufferedImage.TYPE_BYTE_INDEXED -> "TYPE_BYTE_INDEXED";
            case BufferedImage.TYPE_CUSTOM -> "TYPE_CUSTOM";
            default -> "UNKNOWN_TYPE_" + type;
        };
    }
} 