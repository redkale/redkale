/*
 */
package org.redkale.util;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

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

    default String coverNewValue() {
        return ResourceChangeEvent.cover(newValue());
    }

    default String coverOldValue() {
        return ResourceChangeEvent.cover(oldValue());
    }

    public static boolean containsName(ResourceEvent[] events, String... names) {
        if (events == null || events.length == 0 || names.length == 0) return false;
        for (ResourceEvent event : events) {
            if (Utility.contains(names, event.name())) return true;
        }
        return false;
    }

    public static List<ResourceEvent> create(Properties oldProps, Properties newProps) {
        List<ResourceEvent> rs = new ArrayList<>();
        if (oldProps == null && newProps == null) {
            return rs;
        }
        if (oldProps == null) {
            newProps.forEach((k, v) -> rs.add(ResourceEvent.create(k.toString(), v, null)));
        } else if (newProps == null) {
            oldProps.forEach((k, v) -> rs.add(ResourceEvent.create(k.toString(), null, v)));
        } else {
            newProps.forEach((k, v) -> {
                String oldVal = oldProps.getProperty(k.toString());
                if (!Objects.equals(v, oldVal)) {
                    rs.add(ResourceEvent.create(k.toString(), v, oldVal));
                }
            });
            oldProps.forEach((k, v) -> {
                if (!newProps.containsKey(k)) {
                    rs.add(ResourceEvent.create(k.toString(), null, v));
                }
            });
        }
        return rs;
    }

    public static <V> ResourceEvent<V> create(String name, V newValue, V oldValue) {
        return new ResourceChangeEvent<>(name, newValue, oldValue);
    }

    public static class ResourceChangeEvent<T> implements ResourceEvent<T> {

        private static final Predicate<String> numRegx = Pattern.compile("^(\\-|\\+)?\\d+(\\.\\d+)?$").asPredicate();

        protected String name;

        protected T newValue;

        protected T oldValue;

        static <T> String cover(T val) {
            if (val == null) return null;
            String str = val.toString();
            if ("false".equalsIgnoreCase(str)) return str;
            if (str.length() <= 4) return str;
            if (numRegx.test(str)) return str;
            return str.substring(0, 2) + "***" + str.substring(str.length() - 2);
        }

        @ConstructorParameters({"name", "newValue", "oldValue"})
        public ResourceChangeEvent(String name, T newValue, T oldValue) {
            this.name = name;
            this.newValue = newValue;
            this.oldValue = oldValue;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public T newValue() {
            return newValue;
        }

        @Override
        public T oldValue() {
            return oldValue;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public T getNewValue() {
            return newValue;
        }

        public void setNewValue(T newValue) {
            this.newValue = newValue;
        }

        public T getOldValue() {
            return oldValue;
        }

        public void setOldValue(T oldValue) {
            this.oldValue = oldValue;
        }

        @Override
        public String toString() {
            return "{name = " + name() + ", newValue = " + newValue() + ", oldValue = " + oldValue() + "}";
        }
    }
}
