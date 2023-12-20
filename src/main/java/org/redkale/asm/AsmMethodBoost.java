/*
 *
 */
package org.redkale.asm;

import java.lang.reflect.Method;
import java.util.Collection;
import org.redkale.annotation.Nullable;

/**
 * 生产动态字节码的方法扩展器， 可以进行方法加强动作
 *
 * @param <T> 泛型
 *
 * @since 2.8.0
 */
public interface AsmMethodBoost<T> {

    public static AsmMethodBoost create(Collection<AsmMethodBoost> list) {
        return new AsmMethodBoosts(list);
    }

    public static AsmMethodBoost create(AsmMethodBoost... items) {
        return new AsmMethodBoosts(items);
    }

    /**
     * 对方法进行动态加强处理
     *
     * @param cw            动态字节码Writer
     * @param newDynName    动态新类名
     * @param fieldPrefix   动态字段的前缀
     * @param method        操作的方法
     * @param newMethodName 新的方法名, 可能为null
     *
     * @return 下一个新的方法名，不做任何处理应返回参数newMethodName
     */
    public String doMethod(ClassWriter cw, String newDynName, String fieldPrefix, Method method, @Nullable String newMethodName);

    /** 处理所有动态方法后调用
     *
     * @param cw          动态字节码Writer
     * @param newDynName  动态新类名
     * @param fieldPrefix 动态字段的前缀
     */
    public void doAfterMethods(ClassWriter cw, String newDynName, String fieldPrefix);

    /**
     * 实例对象进行操作，通常用于给动态的字段赋值
     *
     * @param service 实例对象
     */
    public void doInstance(T service);

    /**
     * 生产动态字节码的方法扩展器， 可以进行方法加强动作
     *
     * @param <T> 泛型
     *
     * @since 2.8.0
     */
    static class AsmMethodBoosts<T> implements AsmMethodBoost<T> {

        private final AsmMethodBoost[] items;

        public AsmMethodBoosts(Collection<AsmMethodBoost> list) {
            this.items = list.toArray(new AsmMethodBoost[list.size()]);
        }

        public AsmMethodBoosts(AsmMethodBoost... items) {
            this.items = items;
        }

        @Override
        public String doMethod(ClassWriter cw, String newDynName, String fieldPrefix, Method method, String newMethodName) {
            String newName = newMethodName;
            for (AsmMethodBoost item : items) {
                if (item != null) {
                    newName = item.doMethod(cw, newDynName, fieldPrefix, method, newName);
                }
            }
            return newName;
        }

        @Override
        public void doAfterMethods(ClassWriter cw, String newDynName, String fieldPrefix) {
            for (AsmMethodBoost item : items) {
                if (item != null) {
                    item.doAfterMethods(cw, newDynName, fieldPrefix);
                }
            }
        }

        @Override
        public void doInstance(T service) {
            for (AsmMethodBoost item : items) {
                if (item != null) {
                    item.doInstance(service);
                }
            }
        }

    }
}
