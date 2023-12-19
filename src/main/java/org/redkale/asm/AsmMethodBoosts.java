/*
 *
 */
package org.redkale.asm;

import java.lang.reflect.Method;

/**
 * 生产动态字节码的方法扩展器， 可以进行方法加强动作
 *
 * @param <T> 泛型
 *
 * @since 2.8.0
 */
public class AsmMethodBoosts<T> implements AsmMethodBoost<T> {

    private final AsmMethodBoosts[] items;

    public AsmMethodBoosts(AsmMethodBoosts... items) {
        this.items = items;
    }

    @Override
    public String doMethod(ClassWriter cw, Method method, String newMethodName) {
        String newName = newMethodName;
        for (AsmMethodBoosts item : items) {
            if (item != null) {
                newName = item.doMethod(cw, method, newName);
            }
        }
        return newName;
    }

    @Override
    public void doInstance(T service) {
        for (AsmMethodBoosts item : items) {
            if (item != null) {
                item.doInstance(service);
            }
        }
    }

}
