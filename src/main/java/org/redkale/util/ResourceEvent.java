/*
 */
package org.redkale.util;

/**
 * 详情见: https://redkale.org
 *
 * @param <T> 泛型
 *
 * @author zhangjx
 * @since 2.7.0
 */
public interface ResourceEvent<T> {

    public String name();

    public T newValue();

    public T oldValue();
}
