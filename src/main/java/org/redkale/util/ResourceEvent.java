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

    public static boolean containsName(ResourceEvent[] events, String... names) {
        if (events == null || events.length == 0 || names.length == 0) return false;
        for (ResourceEvent event : events) {
            if (Utility.contains(names, event.name())) return true;
        }
        return false;
    }
}
