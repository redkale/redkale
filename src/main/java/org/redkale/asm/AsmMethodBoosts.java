/*
 *
 */
package org.redkale.asm;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * 生产动态字节码的方法扩展器， 可以进行方法加强动作
 *
 * @param <T> 泛型
 *
 * @since 2.8.0
 */
class AsmMethodBoosts<T> implements AsmMethodBoost<T> {

    private final AsmMethodBoost[] items;

    public AsmMethodBoosts(Collection<AsmMethodBoost> list) {
        this.items = list.toArray(new AsmMethodBoost[list.size()]);
    }

    public AsmMethodBoosts(AsmMethodBoost... items) {
        this.items = items;
    }

    @Override
    public String doMethod(ClassWriter cw, Method method, String newMethodName) {
        String newName = newMethodName;
        for (AsmMethodBoost item : items) {
            if (item != null) {
                newName = item.doMethod(cw, method, newName);
            }
        }
        return newName;
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
