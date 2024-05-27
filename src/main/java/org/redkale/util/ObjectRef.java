/*
 *
 */
package org.redkale.util;

/**
 * 简单的对象引用
 *
 * <p>详情见: https://redkale.org
 *
 * @param <V> 泛型
 * @author zhangjx
 * @since 2.8.0
 */
public final class ObjectRef<V> {

    private V value;

    public ObjectRef(V initialValue) {
        this.value = initialValue;
    }

    public ObjectRef() {}

    public final V get() {
        return this.value;
    }

    public final void set(V newValue) {
        this.value = newValue;
    }

    public final void setIfNull(V newValue) {
        if (this.value == null) {
            this.value = newValue;
        }
    }

    @Override
    public String toString() {
        return String.valueOf(this.value);
    }
}
