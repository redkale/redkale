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

    public static <V> ResourceEvent<V> create(String name, V newValue, V oldValue) {
        return new ResourceChangeEvent<>(name, newValue, oldValue);
    }

    public static class ResourceChangeEvent<T> implements ResourceEvent<T> {

        protected String name;

        protected T newValue;

        protected T oldValue;

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
