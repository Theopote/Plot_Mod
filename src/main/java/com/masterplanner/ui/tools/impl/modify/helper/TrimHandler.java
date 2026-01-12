package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;

import java.util.ArrayList;
import java.util.List;

/**
 * 修剪处理器 - 重构后的简化入口类
 * 负责验证参数和委托具体的修剪逻辑到专门的辅助类
 */
public class TrimHandler implements IModifyHandler {
    
    private final AppState appState;
    private final BoundaryTrimHelper boundaryTrimHelper;
    private final FenceTrimHelper fenceTrimHelper;

    public TrimHandler(AppState appState) {
        this.appState = appState;
        this.boundaryTrimHelper = new BoundaryTrimHelper(appState);
        this.fenceTrimHelper = new FenceTrimHelper(appState);
    }

    @Override
    public ModifyType getModifyType() {
        return ModifyType.TRIM;
    }

    @Override
    public ValidationResult validateModification(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        if (shapes == null || shapes.isEmpty()) {
            return ValidationResult.invalid("没有选择要修剪的图形");
        }
        
        // 检查是否为栅栏模式
        Object fenceModeObj = parameters.getParameter("fenceMode");
        boolean fenceMode = fenceModeObj instanceof Boolean && (Boolean) fenceModeObj;
        
        if (fenceMode) {
            // 栅栏模式验证
            @SuppressWarnings("unchecked")
            List<Vec2d> fencePoints = (List<Vec2d>) parameters.getParameter("fencePoints");
            if (fencePoints == null || fencePoints.size() < 3) {
                return ValidationResult.invalid("栅栏至少需要3个点");
            }
        } else {
            // 边界模式验证
            Object trimPointObj = parameters.getParameter("trimPoint");
            if (!(trimPointObj instanceof Vec2d)) {
                return ValidationResult.invalid("修剪点无效");
            }
            @SuppressWarnings("unchecked")
            List<Shape> boundaryShapes = (List<Shape>) parameters.getParameter("boundaryShapes");
            if (boundaryShapes == null || boundaryShapes.isEmpty()) {
                return ValidationResult.invalid("没有找到边界图形");
            }
        }
        
        return ValidationResult.valid();
    }

    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        Object fenceModeObj = parameters.getParameter("fenceMode");
        boolean fenceMode = fenceModeObj instanceof Boolean && (Boolean) fenceModeObj;
        
        if (fenceMode) {
            return calculateFenceTrimmedShapes(shapes, parameters);
        } else {
            return calculateBoundaryTrimmedShapes(shapes, parameters);
        }
    }

    /**
     * 边界修剪模式：委托到边界修剪辅助类
     */
    private List<Shape> calculateBoundaryTrimmedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        Object trimPointObj = parameters.getParameter("trimPoint");
        Vec2d trimPoint = trimPointObj instanceof Vec2d ? (Vec2d) trimPointObj : null;
        @SuppressWarnings("unchecked")
        List<Shape> boundaryShapes = (List<Shape>) parameters.getParameter("boundaryShapes");
        
        // 委托到边界修剪辅助类
        return boundaryTrimHelper.calculateBoundaryTrimmedShapes(shapes, trimPoint, boundaryShapes);
    }

    /**
     * 栅栏修剪模式：委托到栅栏修剪辅助类
     */
    private List<Shape> calculateFenceTrimmedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        if (parameters == null) {
            System.out.println("[DEBUG] calculateFenceTrimmedShapes - 参数为空，返回原图形");
            return new ArrayList<>(shapes);
        }
        
        @SuppressWarnings("unchecked")
        List<Vec2d> fencePoints = (List<Vec2d>) parameters.getParameter("fencePoints");
        
        // 委托到栅栏修剪辅助类
        return fenceTrimHelper.calculateFenceTrimmedShapes(shapes, fencePoints);
    }

    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, ModifyParameters parameters) {
        Object fenceModeObj = parameters.getParameter("fenceMode");
        boolean fenceMode = fenceModeObj instanceof Boolean && (Boolean) fenceModeObj;

        if (fenceMode) {
            @SuppressWarnings("unchecked")
            List<Vec2d> fencePoints = (List<Vec2d>) parameters.getParameter("fencePoints");
            return fenceTrimHelper.createPreviewShapes(shapes, fencePoints);
        } else {
            Object trimPointObj = parameters.getParameter("trimPoint");
            Vec2d trimPoint = trimPointObj instanceof Vec2d ? (Vec2d) trimPointObj : null;
            @SuppressWarnings("unchecked")
            List<Shape> boundaryShapes = (List<Shape>) parameters.getParameter("boundaryShapes");
            return boundaryTrimHelper.createPreviewShapes(shapes, trimPoint, boundaryShapes);
        }
    }

    @Override
    public ModifyCommand createModifyCommand(List<Shape> originalShapes, 
                                           List<Shape> modifiedShapes, 
                                           IModifyHandler.ModifyParameters parameters) {
        return new ModifyCommand(originalShapes, modifiedShapes, appState);
    }
}