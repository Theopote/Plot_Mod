package com.masterplanner.core.group;

import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.selection.SelectionChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图形组管理器
 * 负责处理图形的成组和解组操作
 */
public class ShapeGroupManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShapeGroupManager.class);
    
    private final AppState appState;
    private final EventBus eventBus;
    
    public ShapeGroupManager(AppState appState, EventBus eventBus) {
        this.appState = appState;
        this.eventBus = eventBus;
    }
    
    /**
     * 将选中的图形成组
     * @return 成组结果
     */
    public GroupResult groupSelectedShapes() {
        List<Shape> selectedShapes = appState.getSelectedShapes();
        
        // 验证是否有选中的图形
        if (selectedShapes.isEmpty()) {
            return new GroupResult(false, "没有选中的图形，无法进行成组操作");
        }
        
        // 验证选中的图形数量
        if (selectedShapes.size() < 2) {
            return new GroupResult(false, "至少需要选择2个图形才能进行成组操作");
        }
        
        // 检查是否有图形已经在组中
        List<Shape> alreadyInGroup = selectedShapes.stream()
                .filter(Shape::isInGroup)
                .toList();
        
        if (!alreadyInGroup.isEmpty()) {
            return new GroupResult(false, 
                String.format("选中的图形中有%d个已经在组中，请先解组后再进行成组操作", alreadyInGroup.size()));
        }
        
        try {
            // 生成新的组ID
            String groupId = UUID.randomUUID().toString();
            
            // 将所有选中的图形加入组
            for (Shape shape : selectedShapes) {
                shape.setGroupId(groupId);
            }
            
            // 将第一个图形设置为组代表
            selectedShapes.getFirst().setIsGroup(true);
            
            LOGGER.info("成组操作完成: 将{}个图形组成组ID为{}的组", selectedShapes.size(), groupId);
            
            // 更新选择状态 - 只选中组代表
            clearAllSelections();
            selectedShapes.getFirst().setSelected(true);
            
            // 发布选择变更事件
            eventBus.publish(new SelectionChangedEvent(List.of(selectedShapes.getFirst()), appState));
            
            return new GroupResult(true, 
                String.format("成功将%d个图形组成一组", selectedShapes.size()));
                
        } catch (Exception e) {
            LOGGER.error("成组操作失败", e);
            return new GroupResult(false, "成组操作失败: " + e.getMessage());
        }
    }
    
    /**
     * 解组选中的图形组
     * @return 解组结果
     */
    public GroupResult ungroupSelectedShapes() {
        List<Shape> selectedShapes = appState.getSelectedShapes();
        
        // 验证是否有选中的图形
        if (selectedShapes.isEmpty()) {
            return new GroupResult(false, "没有选中的图形，无法进行解组操作");
        }
        
        // 查找选中的组
        List<Shape> groupRepresentatives = selectedShapes.stream()
                .filter(Shape::isGroup)
                .toList();
        
        if (groupRepresentatives.isEmpty()) {
            return new GroupResult(false, "选中的图形不是组，无法进行解组操作");
        }
        
        try {
            int totalUngrouped = 0;
            List<Shape> newSelectedShapes = new ArrayList<>();
            
            for (Shape groupRep : groupRepresentatives) {
                String groupId = groupRep.getGroupId();
                if (groupId == null) {
                    continue;
                }
                
                // 查找该组的所有成员
                List<Shape> groupMembers = findGroupMembers(groupId);
                
                // 解除组关系
                for (Shape member : groupMembers) {
                    member.setGroupId(null);
                    member.setIsGroup(false);
                    member.setSelected(true); // 解组后选中所有原组成员
                    newSelectedShapes.add(member);
                }
                
                totalUngrouped += groupMembers.size();
                LOGGER.info("解组操作完成: 解散了包含{}个图形的组(ID: {})", groupMembers.size(), groupId);
            }
            
            // 更新选择状态
            clearAllSelections();
            for (Shape shape : newSelectedShapes) {
                shape.setSelected(true);
            }
            
            // 发布选择变更事件
            eventBus.publish(new SelectionChangedEvent(newSelectedShapes, appState));
            
            return new GroupResult(true, 
                String.format("成功解组，共解散了%d个图形", totalUngrouped));
                
        } catch (Exception e) {
            LOGGER.error("解组操作失败", e);
            return new GroupResult(false, "解组操作失败: " + e.getMessage());
        }
    }
    
    /**
     * 查找指定组的所有成员
     * @param groupId 组ID
     * @return 组成员列表
     */
    private List<Shape> findGroupMembers(String groupId) {
        return appState.getShapes().stream()
                .filter(shape -> Objects.equals(shape.getGroupId(), groupId))
                .collect(Collectors.toList());
    }
    
    /**
     * 清除所有图形的选中状态
     */
    private void clearAllSelections() {
        for (Shape shape : appState.getShapes()) {
            shape.setSelected(false);
        }
    }
    
    /**
     * 检查是否可以进行成组操作
     * @return 检查结果
     */
    public GroupValidationResult canGroup() {
        List<Shape> selectedShapes = appState.getSelectedShapes();
        
        if (selectedShapes.isEmpty()) {
            return new GroupValidationResult(false, "没有选中的图形");
        }
        
        if (selectedShapes.size() < 2) {
            return new GroupValidationResult(false, "至少需要选择2个图形");
        }
        
        long alreadyInGroupCount = selectedShapes.stream()
                .filter(Shape::isInGroup)
                .count();
        
        if (alreadyInGroupCount > 0) {
            return new GroupValidationResult(false, 
                String.format("有%d个图形已经在组中", alreadyInGroupCount));
        }
        
        return new GroupValidationResult(true, "可以进行成组操作");
    }
    
    /**
     * 检查是否可以进行解组操作
     * @return 检查结果
     */
    public GroupValidationResult canUngroup() {
        List<Shape> selectedShapes = appState.getSelectedShapes();
        
        if (selectedShapes.isEmpty()) {
            return new GroupValidationResult(false, "没有选中的图形");
        }
        
        long groupCount = selectedShapes.stream()
                .filter(Shape::isGroup)
                .count();
        
        if (groupCount == 0) {
            return new GroupValidationResult(false, "选中的图形不是组");
        }
        
        return new GroupValidationResult(true, 
            String.format("可以解组%d个组", groupCount));
    }
    
    /**
     * 组操作结果
     */
    public static class GroupResult {
        private final boolean success;
        private final String message;
        
        public GroupResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    /**
     * 组验证结果
     */
    public static class GroupValidationResult {
        private final boolean valid;
        private final String message;
        
        public GroupValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
    }
}