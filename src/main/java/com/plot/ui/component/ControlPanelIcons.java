package com.plot.ui.component;

import com.plot.core.resource.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class ControlPanelIcons {
    private static final Logger LOGGER = LoggerFactory.getLogger(ControlPanelIcons.class);
    // Plot Logo
    public static final String LOGO = "logo.png";
    
    // 文件操作工具组
    public static final String NEW_FILE = "new_file.png";
    public static final String SAVE = "save.png";
    public static final String IMPORT = "import.png";
    public static final String UNDO = "undo.png";
    public static final String REDO = "redo.png";

    // 视图控制工具组
    public static final String CAMERA = "camera.png";
    public static final String CAMERA_ORTHO = "camera_ortho.png";
    public static final String LOCK_CLOSED = "lock_closed.png";
    public static final String LOCK_OPEN = "lock_open.png";

    // 工具设置组
    public static final String MAGNET = "magnet.png";
    public static final String CLEAR = "clear.png";
    public static final String BLOCK_CONFIG = "block_config.png";
    public static final String LINE_TO_BLOCK = "line_to_block.png";
    public static final String PROJECTION = "projection.png";
    
    public static final String GRID = "grid.png";  // 修改为包含文件扩展名
    
    // 关闭按钮
    public static final String CLOSE = "close.png";

    private static final Map<String, Integer> textureCache = new HashMap<>();
    private static final ResourceManager resourceManager = ResourceManager.getInstance();

    private ControlPanelIcons() {
        // 私有构造函数防止实例化
    }

    /**
     * 加载图标纹理
     */
    public static void loadTextures() {
        LOGGER.info("Loading control panel icon textures...");
        try {
            // 先检查所有图标的尺寸
            checkIconsSize();
            
            loadTexture(LOGO);
            
            // 文件操作工具组
            loadTexture(NEW_FILE);
            loadTexture(SAVE);
            loadTexture(IMPORT);
            loadTexture(UNDO);
            loadTexture(REDO);
            
            // 视图控制工具组
            loadTexture(CAMERA);
            loadTexture(LOCK_CLOSED);
            loadTexture(LOCK_OPEN);

            // 工具设置组
            loadTexture(MAGNET);
            loadTexture(GRID);  // 使用loadTexture方法加载网格图标
            loadTexture(CLEAR);
            loadTexture(BLOCK_CONFIG);
            loadTexture(LINE_TO_BLOCK);
            loadTexture(PROJECTION);
            
            // 关闭按钮
            loadTexture(CLOSE);
            
            LOGGER.info("Control panel icon textures loaded successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to load control panel icon textures", e);
        }
    }

    /**
     * 检查所有图标的尺寸
     */
    private static void checkIconsSize() {
        LOGGER.info("检查控制面板图标尺寸...");
        try {
            // 获取所有图标文件的路径
            String[] iconNames = {
                LOGO, NEW_FILE, SAVE, IMPORT, UNDO, REDO,
                CAMERA, CAMERA_ORTHO, LOCK_CLOSED, LOCK_OPEN,
                MAGNET, GRID, CLEAR, BLOCK_CONFIG, LINE_TO_BLOCK, PROJECTION,
                CLOSE
            };
            
            for (String iconName : iconNames) {
                try {
                    // 资源路径在资源包中必须使用小写目录名（Minecraft Identifier 区分大小写）
                    String path = "assets/plot/textures/gui/controlpanel/" + iconName;
                    com.plot.utils.ImageUtils.checkImageInfo(path);
                } catch (Exception e) {
                    // 文件不存在时跳过，不中断整个检查流程
                    LOGGER.debug("图标文件不存在，跳过检查: {}", iconName);
                }
            }
            
            LOGGER.info("图标尺寸检查完成");
        } catch (Exception e) {
            LOGGER.error("检查图标尺寸时发生错误", e);
        }
    }

    private static void loadTexture(String iconName) {
        // 移除assets/plot前缀，因为Identifier会自动处理这个
        // 资源目录在 resources 中为 textures/gui/controlpanel（全小写）
        String path = "textures/gui/controlpanel/" + iconName;
        try {
            LOGGER.debug("开始加载纹理: {}", path);
            
            int textureId = resourceManager.loadTexture(path);
            if (textureId > 0) {
                textureCache.put(iconName, textureId);
                LOGGER.debug("成功加载纹理: {} (ID: {})", iconName, textureId);
            } else {
                // 检查文件是否存在
                Identifier identifier = Identifier.of("plot", path);
                boolean exists = net.minecraft.client.MinecraftClient.getInstance()
                    .getResourceManager()
                    .getResource(identifier)
                    .isPresent();
                
                if (!exists) {
                    // 文件不存在时记录警告，但不中断加载流程
                    LOGGER.warn("纹理文件不存在，将跳过: {}", identifier);
                } else {
                    LOGGER.error("加载纹理失败: {} (返回ID为0)", iconName);
                }
            }
        } catch (Exception e) {
            // 加载失败时记录警告，但不中断整个加载流程
            LOGGER.warn("加载纹理时发生错误 {}: {}", iconName, e.getMessage());
        }
    }

    /**
     * 释放资源
     */
    public static void dispose() {
        LOGGER.debug("Disposing control panel icon textures...");
        textureCache.values().forEach(GL11::glDeleteTextures);
        textureCache.clear();
    }


    /**
     * 获取图标的 Identifier
     * @param iconName 图标名称
     * @return 图标的 Identifier
     */
    public static Identifier getIdentifier(String iconName) {
        return Identifier.of("plot", "textures/gui/controlpanel/" + iconName);
    }
    
    /**
     * 安全获取图标纹理ID，如果图标不存在则返回0
     * @param iconName 图标名称
     * @return 纹理ID，如果不存在则返回0
     */
    public static int getTextureId(String iconName) {
        return textureCache.getOrDefault(iconName, 0);
    }

}
