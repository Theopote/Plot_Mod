package com.plot.ui.tools;

import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.core.state.AppState;
import com.plot.core.snap.SnapManager;
import com.plot.ui.tools.impl.drawing.*;
import com.plot.ui.canvas.Canvas;
import com.plot.core.command.CommandManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具工厂类
 * <p>
 * 负责创建绘图工具实例，统一管理依赖注入。
 * 这种方式解决了工具构造函数中直接调用单例 getInstance() 的问题，
 * 降低了耦合度，提高了可测试性。
 * <p>
 * 优势：
 * 1. 集中管理依赖：所有依赖在工厂中统一获取和注入
 * 2. 延迟初始化：工具创建时依赖已经准备好
 * 3. 易于测试：可以注入Mock对象进行单元测试
 * 4. 扩展性好：新增工具时只需在工厂中添加创建方法
 * 5. 初始化顺序安全：避免了单例未准备好的问题
 */
public class ToolFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolFactory.class);
    
    private final IAppState appState;
    private final ISnapManager snapManager;
    private final Canvas canvas;

    /**
     * 构造函数
     * @param appState 应用状态管理器（不能为null）
     * @param snapManager 吸附管理器（可以为null）
     * @param canvas 画布实例（某些工具需要）
     * @param commandManager 命令管理器（某些工具需要）
     */
    public ToolFactory(IAppState appState, ISnapManager snapManager, 
                       Canvas canvas, CommandManager commandManager) {
        this.appState = appState;
        this.snapManager = snapManager;
        this.canvas = canvas;

        LOGGER.debug("ToolFactory 初始化完成");
    }
    
    // ====== 绘图工具创建方法 ======
    
    public CircleTool createCircleTool() {
        LOGGER.debug("创建 CircleTool，使用依赖注入");
        return new CircleTool(appState, snapManager);
    }
    
    public LineTool createLineTool() {
        LOGGER.debug("创建 LineTool，使用依赖注入");
        return new LineTool(appState, snapManager);
    }
    
    public RectangleTool createRectangleTool() {
        LOGGER.debug("创建 RectangleTool，使用依赖注入");
        return new RectangleTool(appState, snapManager);
    }
    
    // 其他工具的创建方法可以逐步添加...
    
    public EllipseTool createEllipseTool() {
        LOGGER.debug("创建 EllipseTool，使用依赖注入");
        return new EllipseTool(appState, snapManager);
    }
    
    public FreeDrawTool createFreeDrawTool() {
        LOGGER.debug("创建 FreeDrawTool，使用依赖注入");
        return new FreeDrawTool(appState, snapManager);
    }
    
    public PolygonTool createPolygonTool() {
        LOGGER.debug("创建 PolygonTool，使用依赖注入");
        return new PolygonTool(appState, snapManager);
    }
    
    public ArcTool createArcTool() {
        LOGGER.debug("创建 ArcTool，使用依赖注入");
        return new ArcTool(appState, snapManager);
    }
    
    public SemicircleTool createSemicircleTool() {
        LOGGER.debug("创建 SemicircleTool，使用依赖注入");
        return new SemicircleTool((AppState) appState, (SnapManager) snapManager);
    }
    
    public StarTool createStarTool() {
        LOGGER.debug("创建 StarTool，使用依赖注入");
        return new StarTool(appState, snapManager);
    }
    
    public CatenaryLineTool createCatenaryLineTool() {
        LOGGER.debug("创建 CatenaryLineTool，使用依赖注入");
        return new CatenaryLineTool(appState, snapManager);
    }
    
    public PolylineTool createPolylineTool() {
        LOGGER.debug("创建 PolylineTool，使用依赖注入");
        return new PolylineTool(appState, snapManager);
    }
    
    public SineCurveTool createSineCurveTool() {
        LOGGER.debug("创建 SineCurveTool，使用依赖注入");
        return new SineCurveTool((AppState) appState, (SnapManager) snapManager);
    }
    
    public SpiralTool createSpiralTool() {
        LOGGER.debug("创建 SpiralTool，使用依赖注入");
        return new SpiralTool((AppState) appState, (SnapManager) snapManager);
    }
    
    public SplineTool createSplineTool() {
        LOGGER.debug("创建 SplineTool，使用依赖注入");
        return new SplineTool(appState, snapManager);
    }

    // ====== Getter方法 ======
    
    public IAppState getAppState() {
        return appState;
    }
    
    public ISnapManager getSnapManager() {
        return snapManager;
    }
    
    public Canvas getCanvas() {
        return canvas;
    }

} 