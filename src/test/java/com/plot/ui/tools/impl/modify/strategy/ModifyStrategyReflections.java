package com.plot.ui.tools.impl.modify.strategy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Modify strategy 单元测试共用的反射辅助。
 */
final class ModifyStrategyReflections {

    private ModifyStrategyReflections() {
    }

    static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    static <T> T getField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    static boolean getBooleanField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    static Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object enumValue(String className, String constant) throws Exception {
        Class<?> enumClass = Class.forName(className);
        Class<? extends Enum> rawEnum = (Class<? extends Enum>) enumClass;
        return Enum.valueOf(rawEnum, constant);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
